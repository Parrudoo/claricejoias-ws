package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.exceptions.RegraNegocioException; // 👇 IMPORTANTE: Importe a sua exceção!
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class KeycloakUserService {

    @Autowired
    private Keycloak keycloak;

    private final String REALM_NAME = "claricejoias";

    /**
     * Fluxo para Clientes: Cadastro direto com senha definida no modal da loja.
     */
    public void criarUsuarioCliente(String email, String senha, String nomeCompleto) {
        UserRepresentation user = criarRepresentacaoBasica(email, nomeCompleto);

        Response response = keycloak.realm(REALM_NAME).users().create(user);
        processarResposta(response, senha, "cliente");
    }

    /**
     * Fluxo para Funcionários (ADM): Cadastro sem senha.
     * O Keycloak exigirá que ele crie a senha no primeiro acesso.
     */
    public void criarUsuarioFuncionario(String email, String nomeCompleto) {
        UserRepresentation user = criarRepresentacaoBasica(email, nomeCompleto);

        // A MÁGICA: Define que o usuário PRECISA resetar a senha ao entrar
        user.setRequiredActions(Collections.singletonList("UPDATE_PASSWORD"));

        Response response = keycloak.realm(REALM_NAME).users().create(user);

        // Passamos null na senha pois ele mesmo vai criar
        processarResposta(response, null, "ADMIN");
    }

    // --- MÉTODOS AUXILIARES PARA LIMPEZA DO CÓDIGO ---

    private UserRepresentation criarRepresentacaoBasica(String email, String nomeCompleto) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(nomeCompleto);
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

    private void processarResposta(Response response, String senha, String roleName) {
        if (response.getStatus() == 201) {
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");

            // Se foi enviada uma senha (caso do cliente), define agora
            if (senha != null) {
                definirSenha(userId, senha);
            }

            // Atribui o cargo (cliente ou admin)
            atribuirRole(userId, roleName);

            System.out.println("Usuário [" + roleName + "] criado com sucesso!");
        } else if (response.getStatus() == 409) {
            // 👇 AQUI ESTÁ A CORREÇÃO! Usando a classe que o Interceptador escuta.
            throw new RegraNegocioException("Este e-mail já está cadastrado.");
        } else {
            // 👇 Também ajustei aqui para não vazar erro genérico pro Front
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