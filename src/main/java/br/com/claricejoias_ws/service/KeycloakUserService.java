package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.repository.LeadRepository;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private final Keycloak keycloak;
    private final ClienteRepository clienteRepository;
    private final LeadRepository leadRepository;
    private final EvolutionApiService evolutionApiService;

    private final String REALM_NAME = "claricejoias";

    /**
     * Fluxo para Clientes: Cadastro direto com senha definida no modal da loja.
     * Agora recebe o visitorId para aproveitar os dados do Lead!
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Garante que se o banco falhar, o processo reverta com segurança
    public void criarUsuarioCliente(String email, String senha, String nomeCompleto, String whatsapp, String visitorId) {
        String whatsappLimpo = (whatsapp != null) ? whatsapp.replaceAll("[^0-9]", "") : null;

        if (clienteRepository.existsByWhatsapp(whatsappLimpo)) {
            throw new RegraNegocioException("Este número de WhatsApp já está vinculado a outra conta. Faça login ou recupere a senha.");
        }

        // AGORA PASSAMOS OS DADOS CORRETOS: email, nome e whatsapp
        UserRepresentation user = criarRepresentacaoBasica(email, nomeCompleto, whatsappLimpo);

        Response response = keycloak.realm(REALM_NAME).users().create(user);

        // O processarResposta continua recebendo a senha (o PIN aleatório) para setar no Keycloak
        String userId = processarResposta(response, senha, "cliente");

        // 3. Prepara o Cliente no banco de dados local
        Cliente novoCliente = new Cliente();
        novoCliente.setUsuarioId(userId); // Esse é o vínculo oficial com o Keycloak!
        novoCliente.setEmail(email);
        novoCliente.setWhatsapp(whatsapp);
        novoCliente.setNome(nomeCompleto);

        // =========================================================
        // 4. A MÁGICA DO FUNIL DE VENDAS E MARKETING (LEAD)
        // =========================================================
        Lead leadDoMarketing = null;

        if (visitorId != null && !visitorId.isEmpty()) {
            // Tenta buscar o Lead pelo rastro do navegador
            leadDoMarketing = leadRepository.findByVisitorId(visitorId).orElse(null);

            // A TRAVA DO COMPUTADOR PÚBLICO
            // Se o lead encontrado já possui um usuarioId, significa que o primeiro usuário
            // já usou esse PC e criou a conta dele. Como um SEGUNDO usuário está criando
            // uma conta nova agora, nós anulamos a busca para forçar a criação de um Lead virgem.
            if (leadDoMarketing != null && leadDoMarketing.getUsuarioId() != null) {
                leadDoMarketing = null;
            }
        }

        if (leadDoMarketing != null) {
            // CENÁRIO A: O cara já era um Lead ANÔNIMO (ninguém registrou conta com esse PC ainda)
            // Vinculamos ele ao novo usuário oficial
            leadDoMarketing.setUsuarioId(userId);
            leadDoMarketing.setNome(nomeCompleto); // Atualiza com o nome oficial do cadastro
            leadDoMarketing.setEmail(email);       // Atualiza o email caso ele tenha mudado
            leadDoMarketing.setWhatsapp(whatsapp);

            // Se ele deixou o WhatsApp lá atrás na captura, a gente garante no perfil do Cliente!
            if (leadDoMarketing.getWhatsapp() != null) {
                novoCliente.setWhatsapp(leadDoMarketing.getWhatsapp());
            }
        } else {
            // CENÁRIO B: PC Público (Lead sobrescrito) ou Cadastro Direto. Criamos um Lead novinho em folha!
            leadDoMarketing = new Lead();

            // Gera um UUID novo na marra, ignorando o visitorId "sujo" do PC público,
            // ou usa um novo caso não tenha vindo nada do front.
            leadDoMarketing.setVisitorId(java.util.UUID.randomUUID().toString());
            leadDoMarketing.setUsuarioId(userId);
            leadDoMarketing.setNome(nomeCompleto);
            leadDoMarketing.setEmail(email);
            leadDoMarketing.setWhatsapp(whatsapp); // Já salva o Zap na criação também!
        }

        // 5. Salva o Lead (seja ele atualizado ou novinho em folha)
        // Isso garante que ele vai aparecer no LeadsDashboard e no seu Job de WhatsApp do Spring Batch!
        leadRepository.save(leadDoMarketing);

        // =========================================================
        // 6. Salva o cliente oficial no PostgreSQL para liberar as compras
        // =========================================================
        clienteRepository.save(novoCliente);

        System.out.println("Cliente " + nomeCompleto + " inserido no Keycloak, Cliente e na Esteira de Marketing (Lead)!");
    }


    public void redefinirSenhaTemporaria(String userId, String novaSenha) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(novaSenha);
        credential.setTemporary(true); // OBRIGA a trocar no login

        keycloak.realm(REALM_NAME).users().get(userId).resetPassword(credential);
    }

    /**
     * Fluxo para Funcionários (ADM): Cadastro sem senha.
     */
//    public void criarUsuarioFuncionario(String email, String nomeCompleto) {
//        UserRepresentation user = criarRepresentacaoBasica(email, nomeCompleto);
//        user.setRequiredActions(Collections.singletonList("UPDATE_PASSWORD"));
//        Response response = keycloak.realm(REALM_NAME).users().create(user);
//        processarResposta(response, null, "ADMIN");
//    }



    public void recuperarSenhaViaWhatsApp(String whatsapp) {
        String whatsappLimpo = whatsapp.replaceAll("[^0-9]", "");

        // 1. Verifica se o cliente existe
        Cliente cliente = clienteRepository.findByWhatsapp(whatsappLimpo)
                .orElseThrow(() -> new RegraNegocioException("Número de WhatsApp não encontrado no sistema."));

        // 2. Gera a nova senha provisória de 6 dígitos
        String pinProvisorio = String.format("%06d", new Random().nextInt(999999));

        // 3. Atualiza a senha direto no Keycloak (como Temporária)
        // O userId do Keycloak você salvou na entidade Cliente!
        redefinirSenhaTemporaria(cliente.getUsuarioId(), pinProvisorio);

        // 4. Dispara a mensagem via Evolution API
        String mensagem = String.format(
                "Olá *%s*! 🔒\n\nVocê solicitou a recuperação de senha na Clarice Joias.\n" +
                        "Sua nova senha de acesso provisória é: *%s*\n\n" +
                        "Acesse o site e faça login com ela. O sistema pedirá para você criar uma nova senha definitiva logo em seguida.",
                cliente.getNome(), pinProvisorio
        );

        evolutionApiService.enviarMensagemTexto(whatsappLimpo, mensagem);
    }


    private UserRepresentation criarRepresentacaoBasica(String email, String nomeCompleto, String whatsapp) {
        UserRepresentation user = new UserRepresentation();

        // Username agora é o WhatsApp (melhor para o login via Evolution API)
        user.setUsername(whatsapp);
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(false);

        // A MÁGICA: Obriga o usuário a mudar a senha no primeiro login
        user.setRequiredActions(java.util.Collections.singletonList("UPDATE_PASSWORD"));

        // Separação do nome
        String[] nomes = nomeCompleto.trim().split(" ", 2);
        user.setFirstName(nomes[0]);
        if (nomes.length > 1) {
            user.setLastName(nomes[1]);
        }

        // Adiciona o WhatsApp nos atributos para consulta posterior
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("whatsapp", java.util.Collections.singletonList(whatsapp));
        user.setAttributes(attributes);

        return user;
    }

    /**
     * Agora este método retorna o ID do Keycloak criado (String)
     */
    private String processarResposta(Response response, String senha, String roleName) {
        if (response.getStatus() == 201) {
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");

            // Valida se a senha não é nula E não está em branco/vazia
            if (senha != null && !senha.trim().isEmpty()) {
                definirSenha(userId, senha);
            }

            // Boa prática: validar a role também para evitar erros no Keycloak
            if (roleName != null && !roleName.trim().isEmpty()) {
                atribuirRole(userId, roleName);
            }

            // Retorna o ID gerado para ser usado na criação do Cliente local
            return userId;

        } else if (response.getStatus() == 409) {
            throw new RegraNegocioException("Este e-mail já está cadastrado.");
        } else {
            throw new RegraNegocioException("Falha ao criar usuário no Keycloak. Tente novamente mais tarde.");
        }
    }

    private void definirSenha(String userId, String senha) {
        CredentialRepresentation credential = new CredentialRepresentation();

        // Mudamos para TRUE: Isso indica que a senha é apenas um PIN provisório
        credential.setTemporary(true);

        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(senha);

        keycloak.realm(REALM_NAME).users().get(userId).resetPassword(credential);
    }

    private void atribuirRole(String userId, String roleName) {
        try {
            RoleRepresentation role = keycloak.realm(REALM_NAME).roles().get(roleName).toRepresentation();
            keycloak.realm(REALM_NAME).users().get(userId).roles().realmLevel().add(Collections.singletonList(role));
        } catch (Exception e) {
            System.err.println("Erro ao atribuir role " + roleName + ". Certifique-se que ela existe no Keycloak.");
        }
    }
}