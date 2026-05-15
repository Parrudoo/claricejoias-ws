package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.InstanceCreateRequest;
import br.com.claricejoias_ws.model.WhatsappInstance;
import br.com.claricejoias_ws.repository.WhatsappInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EvolutionApiService {

    @Value("${evolution.api.url}")
    private String evolutionUrl;
    @Value("${evolution.api.key}")
    private String apikey;
    @Value("${evolution.api.instance}")
    private String instancia;
    private final RestTemplate restTemplate = new RestTemplate();
    private final WhatsappInstanceRepository whatsappInstanceRepository;

    public void enviarMensagemTexto(String numeroDestino, String mensagem) {
        try {
            String url = evolutionUrl + "/message/sendText/" + instancia;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apikey);

            String numeroFormatado = numeroDestino;
            if (!numeroFormatado.startsWith("55")) {
                numeroFormatado = "55" + numeroFormatado;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("number", numeroFormatado);

            Map<String, String> textMessage = new HashMap<>();
            textMessage.put("text", mensagem);
            body.put("textMessage", textMessage);

            Map<String, Integer> options = new HashMap<>();
            options.put("delay", 1200);
            body.put("options", options);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForObject(url, request, String.class);
            System.out.println("Mensagem enviada com sucesso para " + numeroFormatado);

        } catch (Exception e) {
            System.err.println("Erro ao enviar WhatsApp pelo Evolution API: " + e.getMessage());
        }
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apikey);
        return headers;
    }

    public ResponseEntity<String> logoutInstance(String instanceName) {
        String url = evolutionUrl + "/instance/logout/" + instanceName;
        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());
        return restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }

    public ResponseEntity<String> createInstance(InstanceCreateRequest request, String usuarioId) {
        String url = evolutionUrl + "/instance/create";
        HttpEntity<InstanceCreateRequest> entity = new HttpEntity<>(request, getHeaders());
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    public ResponseEntity<String> connectInstance(String instanceName) {
        String url = evolutionUrl + "/instance/connect/" + instanceName;
        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    }

    public ResponseEntity<String> deleteInstance(String instanceName) {
        String url = evolutionUrl + "/instance/delete/" + instanceName;
        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());
        return restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }

    public ResponseEntity<String> setWebhook(String instanceName, Map<String, Object> webhookConfig) {
        String url = evolutionUrl + "/webhook/set/" + instanceName;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(webhookConfig, getHeaders());
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    public ResponseEntity<String> fetchInstances() {
        String baseUrl = evolutionUrl.endsWith("/") ? evolutionUrl.substring(0, evolutionUrl.length() - 1) : evolutionUrl;
        String url = baseUrl + "/instance/fetchInstances";
        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getBody() == null || response.getBody().trim().isEmpty()) {
                return ResponseEntity.ok("[]");
            }
            return response;
        } catch (Exception e) {
            System.err.println("Erro ao buscar instâncias: " + e.getMessage());
            throw e;
        }
    }

    // ========================================================================
    // MÉTODOS DINÂMICOS PARA REVENDEDORES (BASEADO NO ID DO KEYCLOAK)
    // ========================================================================

    public ResponseEntity<String> createInstanceForUser(String usuarioId, String username) {
        // 1. Verifica se o usuário já tem uma instância ativa no banco
        Optional<WhatsappInstance> instanciaExistente = whatsappInstanceRepository.findByUsuarioId(usuarioId);
        if (instanciaExistente.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"message\": \"Usuário já possui uma instância ativa.\"}");
        }

        // 2. Criação do nome e do token
        String cleanUsername = username.replaceAll("[^a-zA-Z0-9]", "");
        String instanceName = "rev_" + cleanUsername + "_" + usuarioId.substring(0, 5);
        String uniqueToken = java.util.UUID.randomUUID().toString();

        Map<String, Object> payload = Map.of(
                "instanceName", instanceName,
                "token", uniqueToken,
                "qrcode", true,
                "integration", "WHATSAPP-BAILEYS"
        );

        String url = evolutionUrl + "/instance/create";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, getHeaders());

        try {
            // 3. Envia o comando para a Evolution API criar a instância
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            // 4. Se deu tudo certo na API (Status 2xx), salvamos no nosso banco de dados
            if (response.getStatusCode().is2xxSuccessful()) {
                WhatsappInstance novaInstancia = new WhatsappInstance();
                novaInstancia.setUsuarioId(usuarioId);
                novaInstancia.setInstanceName(instanceName);
                novaInstancia.setUniqueToken(uniqueToken);
                // Adapte os 'setters' de acordo com as propriedades da sua classe WhatsappInstance

                whatsappInstanceRepository.save(novaInstancia);
            }

            return response;

        } catch (Exception e) {
            System.err.println("Erro ao criar instância para revendedor: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"message\": \"Erro de comunicação com o servidor do WhatsApp.\"}");
        }
    }

    public ResponseEntity<String> connectInstanceByUser(String usuarioId) {
        // 1. Busca a instância que pertence a esse usuário no DB
        Optional<WhatsappInstance> instanceOpt = whatsappInstanceRepository.findByUsuarioId(usuarioId);

        if (instanceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"message\": \"Nenhuma instância encontrada para este usuário.\"}");
        }

        // 2. Extrai o nome e faz a requisição na Evolution API para pegar o QR Code
        String instanceName = instanceOpt.get().getInstanceName();
        String url = evolutionUrl + "/instance/connect/" + instanceName;

        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());

        try {
            return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"message\": \"Erro ao buscar QR Code.\"}");
        }
    }

    public ResponseEntity<String> deleteInstanceByUser(String usuarioId) {
        // 1. Busca a instância que pertence a esse usuário no DB
        Optional<WhatsappInstance> instanceOpt = whatsappInstanceRepository.findByUsuarioId(usuarioId);

        if (instanceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"message\": \"Nenhuma instância encontrada para este usuário.\"}");
        }

        WhatsappInstance instance = instanceOpt.get();
        String instanceName = instance.getInstanceName();
        String url = evolutionUrl + "/instance/delete/" + instanceName;

        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());

        try {
            // 2. Deleta na Evolution API
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);

            // 3. Se a exclusão teve sucesso na API, apagamos do nosso banco de dados
            if (response.getStatusCode().is2xxSuccessful()) {
                whatsappInstanceRepository.delete(instance);
            }

            return response;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"message\": \"Erro ao deletar instância.\"}");
        }
    }

    public ResponseEntity<String> logoutInstanceByUser(String usuarioId) {
        // 1. Busca a instância que pertence a esse usuário no DB
        Optional<WhatsappInstance> instanceOpt = whatsappInstanceRepository.findByUsuarioId(usuarioId);

        if (instanceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"message\": \"Nenhuma instância encontrada para este usuário.\"}");
        }

        // 2. Extrai o nome e faz a requisição de logout na Evolution API
        String instanceName = instanceOpt.get().getInstanceName();
        String url = evolutionUrl + "/instance/logout/" + instanceName;

        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());

        try {
            return restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
        } catch (Exception e) {
            System.err.println("Erro ao desconectar instância: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"message\": \"Erro ao desconectar a instância no servidor.\"}");
        }
    }
}