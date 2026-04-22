package br.com.claricejoias_ws.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

    @Bean
    public Keycloak keycloakAdminClient() {
        // Usando as credenciais de admin que você definiu no docker-compose.yml
        return KeycloakBuilder.builder()
                .serverUrl("http://localhost:8083") // A porta exposta do seu Keycloak
                .realm("master") // O login de administrador master é feito no realm 'master'
                .grantType(OAuth2Constants.PASSWORD)
                .clientId("admin-cli") // Cliente padrão de administração do Keycloak
                .username("admin")
                .password("admin")
                .build();
    }
}