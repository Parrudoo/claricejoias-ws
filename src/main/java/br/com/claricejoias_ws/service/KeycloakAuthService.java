package br.com.claricejoias_ws.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class KeycloakAuthService {

    //  MUDANÇA 1: Apontando para o realm unificado
    private final String REALM_NAME = "claricejoias";

    // O nome do Client que você configurou
    private final String CLIENT_ID = "claricejoias-web";

    //  ATENÇÃO À PORTA: Se o seu Keycloak roda na 8180, mude o 8083 para 8180 aqui.
    private final String KEYCLOAK_TOKEN_URL = "http://host.docker.internal:8083/realms/" + REALM_NAME + "/protocol/openid-connect/token";

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