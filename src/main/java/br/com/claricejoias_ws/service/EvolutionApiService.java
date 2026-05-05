package br.com.claricejoias_ws.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class EvolutionApiService {

    @Value("${evolution.api.url}") // Ex: http://claricejoias-evolution:8080 (Sem barra no final!)
    private String evolutionUrl;

    @Value("${evolution.api.key}") // Ex: sua_apikey_do_evolution
    private String apikey;

    @Value("${evolution.api.instance}") // Ex: claricejoias
    private String instancia;

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
}