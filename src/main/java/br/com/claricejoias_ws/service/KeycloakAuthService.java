package br.com.claricejoias_ws.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class KeycloakAuthService {

    // Ajuste aqui se o nome do seu Realm de clientes for diferente
    private final String REALM_NAME = "claricejoias-clientes";

    // O nome do Client que você configurou no Passo 1
    private final String CLIENT_ID = "claricejoias-web";

    private final String KEYCLOAK_TOKEN_URL = "http://localhost:8083/realms/" + REALM_NAME + "/protocol/openid-connect/token";

    public Map<String, Object> realizarLogin(String email, String senha) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Monta o "corpo" da requisição exigido pelo Keycloak
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", CLIENT_ID);
        formData.add("username", email);
        formData.add("password", senha);
        formData.add("grant_type", "password"); // Tipo de autenticação direta

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            // Dispara o pedido para o Keycloak
            ResponseEntity<Map> response = restTemplate.postForEntity(KEYCLOAK_TOKEN_URL, request, Map.class);
            return response.getBody(); // Retorna os Tokens (Access Token, Refresh Token)

        } catch (HttpClientErrorException e) {
            // Se o usuário errar a senha ou e-mail, o Keycloak devolve 401
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("E-mail ou senha incorretos.");
            }
            throw new RuntimeException("Erro ao autenticar no Keycloak.");
        }
    }
}