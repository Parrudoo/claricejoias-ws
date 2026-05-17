package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.InstanceCreateRequest;
import br.com.claricejoias_ws.model.WhatsappInstance;
import br.com.claricejoias_ws.repository.WhatsappInstanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvolutionApiService {

    @Value("${evolution.api.url}")
    private String evolutionUrl;

    @Value("${evolution.api.key}")
    private String apikey;

    @Value("${evolution.api.instance}")
    private String instanciaGlobal;

    private final RestTemplate restTemplate = new RestTemplate();
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // ENVIO DE MENSAGENS (USADOS PELO RABBITMQ WORKER)
    // ========================================================================

    public boolean enviarTexto(String numeroDestino, String mensagem, String instanciaUso) {
        String url = evolutionUrl + "/message/sendText/" + instanciaUso;
        String numeroFormatado = formatarNumero(numeroDestino);

        Map<String, Object> body = Map.of(
                "number", numeroFormatado,
                "textMessage", Map.of("text", mensagem),
                "options", Map.of("delay", 1200) // Simula digitação humana
        );

        try {
            log.info("Enviando texto via Evolution API para {} usando a instância: {}", numeroFormatado, instanciaUso);
            restTemplate.postForObject(url, new HttpEntity<>(body, getHeaders()), String.class);

            // Se a requisição HTTP retornar sucesso (2xx), retorna true
            return true;
        } catch (Exception e) {
            log.error("Falha ao enviar texto para {}: {}", numeroFormatado, e.getMessage());

            // Repassa a exceção para quem chamou poder tratar (Essencial para o RabbitMQ)
            throw e;
        }
    }

    public void enviarMediaBase64(String numeroDestino, String legenda, String mediaBase64, String instanciaUso) {
        String url = evolutionUrl + "/message/sendMedia/" + instanciaUso;
        String numeroFormatado = formatarNumero(numeroDestino);

        Map<String, Object> body = Map.of(
                "number", numeroFormatado,
                "mediaMessage", Map.of(
                        "mediatype", "image",
                        "caption", legenda != null ? legenda : "",
                        "media", mediaBase64
                ),
                "options", Map.of("delay", 1200)
        );

        log.info("Enviando imagem via Evolution API para {} usando a instância: {}", numeroFormatado, instanciaUso);
        restTemplate.postForObject(url, new HttpEntity<>(body, getHeaders()), String.class);
    }

    // Mantido para compatibilidade com partes antigas do sistema que não passam a instância
    public boolean enviarMensagemTexto(String numeroDestino, String mensagem, String instancia) {
        try {
            // Retorna o resultado (true) do método enviarTexto
            return this.enviarTexto(numeroDestino, mensagem, instancia);
        } catch (Exception e) {
            log.error("Erro ao enviar WhatsApp pelo Evolution API para {}: {}", numeroDestino, e.getMessage());
            // Aqui você pode retornar false para não quebrar a aplicação onde esse método é chamado
            return false;

            // Ou, se esse método for chamado direto pelo Worker do RabbitMQ,
            // o ideal é manter o "throw e;" para que a fila saiba do erro:
            // throw e;
        }
    }

    // ========================================================================
    // GERENCIAMENTO DE INSTÂNCIAS (REVENDEDORES)
    // ========================================================================

    public ResponseEntity<String> createInstanceForUser(String usuarioId, String username, boolean isAdmin) {
        Optional<WhatsappInstance> instanciaExistente = whatsappInstanceRepository.findByUsuarioId(usuarioId);
        if (instanciaExistente.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"message\": \"Usuário já possui uma instância ativa.\"}");
        }

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

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, getHeaders()), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                WhatsappInstance novaInstancia = new WhatsappInstance();
                novaInstancia.setUsuarioId(usuarioId);
                novaInstancia.setInstanceName(instanceName);
                novaInstancia.setUniqueToken(uniqueToken);
                whatsappInstanceRepository.save(novaInstancia);
                log.info("Instância {} criada com sucesso para o usuário {}", instanceName, usuarioId);
            }

            return response;

        } catch (Exception e) {
            log.error("Erro ao criar instância para revendedor {}: {}", usuarioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"message\": \"Erro de comunicação com o servidor do WhatsApp.\"}");
        }
    }

    public ResponseEntity<String> connectInstanceByUser(String usuarioId) {
        Optional<WhatsappInstance> instanceOpt = whatsappInstanceRepository.findByUsuarioId(usuarioId);

        if (instanceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"message\": \"Nenhuma instância encontrada para este usuário.\"}");
        }

        String instanceName = instanceOpt.get().getInstanceName();
        String url = evolutionUrl + "/instance/connect/" + instanceName;

        try {
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(getHeaders()), String.class);
        } catch (Exception e) {
            log.error("Erro ao buscar QR Code para instância {}: {}", instanceName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"message\": \"Erro ao buscar QR Code.\"}");
        }
    }

    public ResponseEntity<String> deleteInstanceByUser(String usuarioId) {
        Optional<WhatsappInstance> instanceOpt = whatsappInstanceRepository.findByUsuarioId(usuarioId);

        if (instanceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"message\": \"Nenhuma instância encontrada para este usuário.\"}");
        }

        WhatsappInstance instance = instanceOpt.get();
        String instanceName = instance.getInstanceName();
        String url = evolutionUrl + "/instance/delete/" + instanceName;

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(getHeaders()), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                whatsappInstanceRepository.delete(instance);
                log.info("Instância {} deletada com sucesso no banco e na API", instanceName);
            }

            return response;
        } catch (Exception e) {
            log.error("Erro ao deletar instância {}: {}", instanceName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"message\": \"Erro ao deletar instância.\"}");
        }
    }

    public ResponseEntity<String> logoutInstanceByUser(String usuarioId) {
        Optional<WhatsappInstance> instanceOpt = whatsappInstanceRepository.findByUsuarioId(usuarioId);

        if (instanceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"message\": \"Nenhuma instância encontrada para este usuário.\"}");
        }

        String instanceName = instanceOpt.get().getInstanceName();
        String url = evolutionUrl + "/instance/logout/" + instanceName;

        try {
            log.info("Desconectando instância: {}", instanceName);
            return restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(getHeaders()), String.class);
        } catch (Exception e) {
            log.error("Erro ao desconectar instância {}: {}", instanceName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"message\": \"Erro ao desconectar a instância no servidor.\"}");
        }
    }

    public ResponseEntity<String> fetchInstances(String usuarioId) {
        Optional<WhatsappInstance> instanceOpt = whatsappInstanceRepository.findByUsuarioId(usuarioId);

        if (instanceOpt.isEmpty()) {
            return ResponseEntity.ok("[]");
        }

        String userInstanceName = instanceOpt.get().getInstanceName();
        String baseUrl = evolutionUrl.endsWith("/") ? evolutionUrl.substring(0, evolutionUrl.length() - 1) : evolutionUrl;
        String url = baseUrl + "/instance/fetchInstances";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(getHeaders()), String.class);
            String responseBody = response.getBody();

            if (responseBody == null || responseBody.trim().isEmpty()) {
                return ResponseEntity.ok("[]");
            }

            JsonNode allInstancesNode = objectMapper.readTree(responseBody);
            ArrayNode filteredInstances = objectMapper.createArrayNode();

            if (allInstancesNode.isArray()) {
                for (JsonNode node : allInstancesNode) {
                    JsonNode nameNode = node.path("instance").path("instanceName");
                    if (nameNode.isMissingNode()) {
                        nameNode = node.path("instanceName");
                    }

                    if (!nameNode.isMissingNode() && userInstanceName.equals(nameNode.asText())) {
                        filteredInstances.add(node);
                        break;
                    }
                }
            }

            return ResponseEntity.ok(objectMapper.writeValueAsString(filteredInstances));

        } catch (Exception e) {
            log.error("Erro ao buscar a instância do usuário na API: {}", e.getMessage());
            throw new RuntimeException("Erro ao buscar a instância", e);
        }
    }

    // ========================================================================
    // GERENCIAMENTO DE INSTÂNCIAS GENÉRICAS (MÉTODOS ANTIGOS/ADMIN)
    // ========================================================================

    public ResponseEntity<String> createInstance(InstanceCreateRequest request, String usuarioId) {
        String url = evolutionUrl + "/instance/create";
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request, getHeaders()), String.class);
    }

    public ResponseEntity<String> connectInstance(String instanceName) {
        String url = evolutionUrl + "/instance/connect/" + instanceName;
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(getHeaders()), String.class);
    }

    public ResponseEntity<String> logoutInstance(String instanceName) {
        String url = evolutionUrl + "/instance/logout/" + instanceName;
        return restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(getHeaders()), String.class);
    }

    public ResponseEntity<String> deleteInstance(String instanceName) {
        String url = evolutionUrl + "/instance/delete/" + instanceName;
        return restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(getHeaders()), String.class);
    }

    public ResponseEntity<String> setWebhook(String instanceName, Map<String, Object> webhookConfig) {
        String url = evolutionUrl + "/webhook/set/" + instanceName;
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(webhookConfig, getHeaders()), String.class);
    }

    // ========================================================================
    // MÉTODOS AUXILIARES
    // ========================================================================

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apikey);
        return headers;
    }

    private String formatarNumero(String numero) {
        if (numero == null) return "";
        String limpo = numero.replaceAll("\\D", "");
        if (!limpo.startsWith("55")) {
            return "55" + limpo;
        }
        return limpo;
    }
}