package br.com.claricejoias_ws.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI clariceJoiasOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clarice Joias API")
                        .description("API para gerenciamento de acervo, categorias e integração com MinIO")
                        .version("v0.0.1")
                        .contact(new Contact()
                                .name("Diego Oliveira Dias")
                                .email("suporte@claricejoias.com.br")));
    }
}
