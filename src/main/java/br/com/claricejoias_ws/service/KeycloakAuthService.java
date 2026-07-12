package br.com.claricejoias_ws.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class KeycloakAuthService {

    // Configurações do Realm
    private final String REALM_NAME = "claricejoias";
    private final String CLIENT_ID = "claricejoias-web";


    @Value("${keycloak.server-url:http://localhost:8083}")
    private String keycloakServerUrl;

    public Map<String, Object> realizarLogin(String email, String senha) {
        RestTemplate restTemplate = new RestTemplate();

        // 1. Monta a URL de forma dinâmica usando a variável injetada
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakServerUrl, REALM_NAME);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // 2. Monta o "corpo" da requisição (OAuth2 Password Grant)
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", CLIENT_ID);
        formData.add("username", email);
        formData.add("password", senha);
        formData.add("grant_type", "password");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            // 3. Dispara o POST usando a 'tokenUrl' dinâmica
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            return (Map<String, Object>) response.getBody();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("E-mail ou senha incorretos.");
            }
            log.error("Erro ao autenticar no Keycloak ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Erro ao autenticar. Tente novamente mais tarde.");
        } catch (Exception e) {
            // Tratamento para erros de conexão (como o Connection Refused)
            log.error("Não foi possível conectar ao servidor de autenticação em {}", tokenUrl, e);
            throw new RuntimeException("Não foi possível conectar ao servidor de autenticação. Tente novamente mais tarde.");
        }
    }
}