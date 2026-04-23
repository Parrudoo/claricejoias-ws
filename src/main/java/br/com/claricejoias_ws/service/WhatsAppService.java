package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.HistoricoDisparo;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.HistoricoDisparoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

@Service
public class WhatsAppService {

    private final RestTemplate restTemplate;
    private final HistoricoDisparoRepository historicoRepository;

    // Puxa o limite de horas do application.properties. Se não achar, usa 24h como padrão.
    @Value("${app.whatsapp.cooldown-horas:24}")
    private int cooldownHoras;

    // Em um cenário ideal, essas variáveis viriam do application.properties usando @Value
    private final String evolutionApiUrl = "http://localhost:8081/message/sendText/claricejoias";
    private final String apiKey = "claricejoias";

    // O Spring injeta o repositório automaticamente no construtor
    public WhatsAppService(HistoricoDisparoRepository historicoRepository) {
        this.restTemplate = new RestTemplate();
        this.historicoRepository = historicoRepository;
    }

    // ATENÇÃO: O método agora recebe o objeto Lead inteiro em vez de só a String do número
    public void enviarMensagemTexto(Lead lead, String texto,String operador) {

        // ==========================================
        // 1. REGRA DE NEGÓCIO: VERIFICAÇÃO DE SPAM
        // ==========================================
        Optional<HistoricoDisparo> ultimoDisparo = historicoRepository.findTopByLeadIdOrderByDataHoraDisparoDesc(lead.getId());

        if (ultimoDisparo.isPresent()) {
            LocalDateTime dataUltimo = ultimoDisparo.get().getDataHoraDisparo();
            LocalDateTime dataLiberacao = dataUltimo.plus(cooldownHoras, ChronoUnit.HOURS);

            // Se o momento atual for ANTES da data de liberação, bloqueia o envio
            if (LocalDateTime.now().isBefore(dataLiberacao)) {
                throw new RegraNegocioException("Aguarde! O próximo disparo para " + lead.getNome() +
                        " só estará liberado em: " + dataLiberacao);
            }
        }

        // ==========================================
        // 2. FORMATAÇÃO DO NÚMERO
        // ==========================================
        String numeroCorreto = lead.getWhatsapp();
        if (numeroCorreto != null && !numeroCorreto.startsWith("55")) {
            numeroCorreto = "55" + numeroCorreto;
        }

        // ==========================================
        // 3. ENVIO PARA A EVOLUTION API
        // ==========================================
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apiKey);

        Map<String, Object> body = Map.of(
                "number", numeroCorreto,
                "textMessage", Map.of("text", texto)
        );

        try {
            restTemplate.postForEntity(evolutionApiUrl, new HttpEntity<>(body, headers), String.class);
            System.out.println("Mensagem enviada com sucesso para: " + numeroCorreto);

            // ==========================================
            // 4. GRAVAÇÃO DO HISTÓRICO NO BANCO
            // ==========================================
            HistoricoDisparo novoHistorico = new HistoricoDisparo(lead, LocalDateTime.now(),operador);
            historicoRepository.save(novoHistorico);

        } catch (Exception e) {
            System.err.println("Falha ao enviar para " + numeroCorreto);
            System.err.println("Motivo do erro: " + e.getMessage());
            throw new RuntimeException("Falha ao enviar mensagem via Evolution API", e);
        }
    }
}