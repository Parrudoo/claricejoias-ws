package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.InstanceCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

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

    public void enviarMensagemTexto(String numeroDestino, String mensagem) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = evolutionUrl + "/message/sendText/" + instancia;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apikey);

            // Garante que o número tenha o DDI do Brasil se não tiver
            String numeroFormatado = numeroDestino;
            if (!numeroFormatado.startsWith("55")) {
                numeroFormatado = "55" + numeroFormatado;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("number", numeroFormatado);

            //  AJUSTE AQUI: O Evolution API 1.8+ exige um objeto textMessage
            Map<String, String> textMessage = new HashMap<>();
            textMessage.put("text", mensagem);
            body.put("textMessage", textMessage);
            // FIM DO AJUSTE

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

    // Método auxiliar para injetar a API Key em todas as requisições
    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apikey);
        return headers;
    }

    // 6. Desconectar Instância (Logout)
    public ResponseEntity<String> logoutInstance(String instanceName) {
        String url = evolutionUrl + "/instance/logout/" + instanceName;
        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());

        return restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }


    public ResponseEntity<String> createInstance(InstanceCreateRequest request) {
        String url = evolutionUrl + "/instance/create";
        HttpEntity<InstanceCreateRequest> entity = new HttpEntity<>(request, getHeaders());

        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    // 2. Conectar Instância (Gera o QR Code em Base64)
    public ResponseEntity<String> connectInstance(String instanceName) {
        String url = evolutionUrl + "/instance/connect/" + instanceName;
        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());

        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    }

    // 3. Deletar Instância
    public ResponseEntity<String> deleteInstance(String instanceName) {
        String url = evolutionUrl + "/instance/delete/" + instanceName;
        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());

        return restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }

    // 4. Configurar Webhook para uma instância existente
    public ResponseEntity<String> setWebhook(String instanceName, Map<String, Object> webhookConfig) {
        String url = evolutionUrl + "/webhook/set/" + instanceName;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(webhookConfig, getHeaders());

        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    // 5. Listar todas as Instâncias
    public ResponseEntity<String> fetchInstances() {
        // 1. Removemos possíveis barras duplas caso evolutionUrl termine com '/'
        String baseUrl = evolutionUrl.endsWith("/") ? evolutionUrl.substring(0, evolutionUrl.length() - 1) : evolutionUrl;
        String url = baseUrl + "/instance/fetchInstances";

        HttpEntity<Void> entity = new HttpEntity<>(getHeaders());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            // LOGS PARA DEBUG NO CONSOLE DO JAVA
            System.out.println("=== TESTE FETCH INSTANCES ===");
            System.out.println("URL Chamada: " + url);
            System.out.println("Status HTTP: " + response.getStatusCode());
            System.out.println("Corpo da Resposta: " + response.getBody());
            System.out.println("=============================");

            // Se a API retornar vazio, forçamos um JSON de array vazio para não quebrar o React
            if (response.getBody() == null || response.getBody().trim().isEmpty()) {
                return ResponseEntity.ok("[]");
            }

            return response;

        } catch (Exception e) {
            System.err.println("Erro ao buscar instâncias: " + e.getMessage());
            throw e;
        }
    }
}