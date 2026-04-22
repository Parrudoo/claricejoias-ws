package br.com.claricejoias_ws.service;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class KeycloakUserService {

    @Autowired
    private Keycloak keycloak;

    // Nome do Realm onde os seus clientes/loja ficam (Ajuste se o seu for diferente)
    private final String REALM_NAME = "claricejoias-clientes";

    public void criarUsuarioCliente(String email, String senha, String nomeCompleto) {

        // 1. Configura os dados básicos do usuário
        UserRepresentation user = new UserRepresentation();
        user.setUsername(email); // Usamos o e-mail como username para facilitar o login
        user.setEmail(email);

        // Divide o nome completo em Primeiro Nome e Sobrenome (O Keycloak pede separado)
        String[] nomes = nomeCompleto.split(" ", 2);
        user.setFirstName(nomes[0]);
        if (nomes.length > 1) {
            user.setLastName(nomes[1]);
        }

        user.setEnabled(true); // Já deixa o usuário ativo
        user.setEmailVerified(false); // Pode colocar true se não for exigir validação de email

        // 2. Dispara a criação no Keycloak
        Response response = keycloak.realm(REALM_NAME).users().create(user);

        if (response.getStatus() == 201) {
            System.out.println("Usuário criado com sucesso no Keycloak!");

            // 3. Opcional: Pegar o ID do usuário recém-criado para gravar a senha
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            definirSenha(userId, senha);

        } else if (response.getStatus() == 409) {
            throw new RuntimeException("Este e-mail já está cadastrado.");
        } else {
            throw new RuntimeException("Falha ao criar usuário no Keycloak. Status: " + response.getStatus());
        }
    }

    private void definirSenha(String userId, String senha) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(false); // Define que a senha é permanente (não pede pra trocar no 1º login)
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(senha);

        // Aplica a senha no usuário criado
        keycloak.realm(REALM_NAME).users().get(userId).resetPassword(credential);
    }
}