package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.repository.VisitanteRepository; // Adicione esse repositório
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private final Keycloak keycloak;
    private final ClienteRepository clienteRepository;
    private final VisitanteRepository visitanteRepository; // Injetado para buscar os dados de Lead

    private final String REALM_NAME = "claricejoias";

    /**
     * Fluxo para Clientes: Cadastro direto com senha definida no modal da loja.
     * Agora recebe o visitorId para aproveitar os dados do Lead!
     */
    @Transactional // Adicionado para garantir que salve no banco de dados com segurança
    public void criarUsuarioCliente(String email, String senha, String nomeCompleto, String visitorId) {
        UserRepresentation user = criarRepresentacaoBasica(email, nomeCompleto);

        // 1. Cria no Keycloak
        Response response = keycloak.realm(REALM_NAME).users().create(user);

        // 2. Processa a resposta e pega o ID gerado pelo Keycloak
        String userId = processarResposta(response, senha, "cliente");

        // 3. CRIA O CLIENTE NO BANCO DE DADOS LOCAL
        Cliente novoCliente = new Cliente();
        novoCliente.setUsuarioId(userId); // Esse é o vínculo com o Keycloak!
        novoCliente.setEmail(email);
        novoCliente.setNome(nomeCompleto);

        // 4. Aproveita os dados de Lead (se o visitante baixou o e-book)
        if (visitorId != null && !visitorId.isEmpty()) {
            visitanteRepository.findByVisitorUuid(visitorId).ifPresent(visitante -> {
                // Se ele tinha deixado o WhatsApp lá atrás, já preenchemos no perfil dele
                if (visitante.getWhatsapp() != null) {
                    novoCliente.setTelefone(visitante.getWhatsapp());
                }

                // Marca o visitante vinculando-o ao novo usuário oficial
                visitante.setUsuarioId(userId);
                visitanteRepository.save(visitante);
            });
        }

        // 5. Salva o cliente oficial no PostgreSQL
        clienteRepository.save(novoCliente);

        System.out.println("Cliente " + nomeCompleto + " criado no Keycloak e no Banco Local com sucesso!");
    }

    /**
     * Fluxo para Funcionários (ADM): Cadastro sem senha.
     */
    public void criarUsuarioFuncionario(String email, String nomeCompleto) {
        UserRepresentation user = criarRepresentacaoBasica(email, nomeCompleto);
        user.setRequiredActions(Collections.singletonList("UPDATE_PASSWORD"));
        Response response = keycloak.realm(REALM_NAME).users().create(user);
        processarResposta(response, null, "ADMIN");
    }

    // --- MÉTODOS AUXILIARES ---

    private UserRepresentation criarRepresentacaoBasica(String email, String nomeCompleto) {
        UserRepresentation user = new UserRepresentation();
        // Setando o username igual ao email é uma boa prática para e-commerce
        user.setUsername(email);
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(false);

        String[] nomes = nomeCompleto.split(" ", 2);
        user.setFirstName(nomes[0]);
        if (nomes.length > 1) {
            user.setLastName(nomes[1]);
        }
        return user;
    }

    /**
     * Agora este método retorna o ID do Keycloak criado (String)
     */
    private String processarResposta(Response response, String senha, String roleName) {
        if (response.getStatus() == 201) {
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");

            if (senha != null) {
                definirSenha(userId, senha);
            }
            atribuirRole(userId, roleName);

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
        credential.setTemporary(false);
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