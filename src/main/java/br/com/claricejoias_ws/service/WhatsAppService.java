package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.FilaDisparo;
import br.com.claricejoias_ws.model.HistoricoDisparo;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.FilaDisparoRepository;
import br.com.claricejoias_ws.repository.HistoricoDisparoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final FilaDisparoRepository filaRepository;

    @Value("${app.whatsapp.cooldown-horas:24}")
    private int cooldownHoras;

    private final String evolutionApiUrl = "http://localhost:8081/message/sendText/claricejoias";
    private final String apiKey = "claricejoias";

    public WhatsAppService(HistoricoDisparoRepository historicoRepository, FilaDisparoRepository filaRepository) {
        this.restTemplate = new RestTemplate();
        this.historicoRepository = historicoRepository;
        this.filaRepository = filaRepository;
    }

    // =========================================================================
    // 1. MÉTODO CHAMADO PELO CONTROLLER/FRONTEND (Apenas Enfileira)
    // =========================================================================
    public void enviarMensagemTexto(Lead lead, String texto, String operador) {

        // ==========================================
        // 1. NOVA TRAVA: Verifica duplicidade na Fila
        // ==========================================
        if (filaRepository.existsByLeadIdAndStatus(lead.getId(), StatusDisparo.PENDENTE)) {
            throw new RegraNegocioException("Operação negada: " + lead.getNome() + " já possui uma mensagem na fila aguardando disparo.");
        }

        // ==========================================
        // 2. TRAVA ANTIGA: Verifica histórico (24h)
        // ==========================================
        Optional<HistoricoDisparo> ultimoDisparo = historicoRepository.findTopByLeadIdOrderByDataHoraDisparoDesc(lead.getId());

        if (ultimoDisparo.isPresent()) {
            LocalDateTime dataUltimo = ultimoDisparo.get().getDataHoraDisparo();
            LocalDateTime dataLiberacao = dataUltimo.plus(cooldownHoras, ChronoUnit.HOURS);

            if (LocalDateTime.now().isBefore(dataLiberacao)) {
                throw new RegraNegocioException("Aguarde! O próximo disparo para " + lead.getNome() +
                        " só estará liberado em: " + dataLiberacao);
            }
        }

        // Salva na tabela FilaDisparo ao invés de enviar para a Evolution API agora
        FilaDisparo fila = new FilaDisparo();
        fila.setLead(lead);
        fila.setTexto(texto);
        fila.setOperador(operador);
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());

        filaRepository.save(fila);
        System.out.println("Mensagem ENFILEIRADA para o lead: " + lead.getNome());
    }

    // =========================================================================
    // 2. MÉTODO DO TRABALHADOR EM SEGUNDO PLANO (Executa a cada 15 segundos)
    // =========================================================================
    @Scheduled(fixedDelay = 15000)
    public void processarFilaDeDisparos() {

        // Pega a mensagem mais antiga que está com status PENDENTE
        Optional<FilaDisparo> disparoOptional = filaRepository.findFirstByStatusOrderByDataCriacaoAsc(StatusDisparo.PENDENTE);

        if (disparoOptional.isEmpty()) {
            return; // Se a fila estiver vazia, encerra silenciosamente até a próxima rodada
        }

        FilaDisparo disparoAtual = disparoOptional.get();
        Lead lead = disparoAtual.getLead();

        String numeroCorreto = lead.getWhatsapp();
        if (numeroCorreto != null && !numeroCorreto.startsWith("55")) {
            numeroCorreto = "55" + numeroCorreto;
        }

        // Prepara requisição para a Evolution
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apiKey);

        Map<String, Object> body = Map.of(
                "number", numeroCorreto,
                "textMessage", Map.of("text", disparoAtual.getTexto())
        );

        try {
            // Dispara via API
            restTemplate.postForEntity(evolutionApiUrl, new HttpEntity<>(body, headers), String.class);
            System.out.println("Fila PROCESSADA: Mensagem enviada via Evolution para " + numeroCorreto);

            // Se der sucesso, atualiza o status na fila
            disparoAtual.setStatus(StatusDisparo.ENVIADO);
            filaRepository.save(disparoAtual);

            // E cria o registro final de Histórico (que é o que aparece no Modal do Frontend)
            HistoricoDisparo novoHistorico = new HistoricoDisparo(lead, LocalDateTime.now(), disparoAtual.getOperador());
            historicoRepository.save(novoHistorico);

        } catch (Exception e) {
            System.err.println("Fila FALHOU: Erro ao enviar para " + numeroCorreto);
            System.err.println("Motivo: " + e.getMessage());

            // Em caso de erro (ex: número inválido ou evolution fora do ar), marca como ERRO e guarda o motivo
            disparoAtual.setStatus(StatusDisparo.ERRO);
            disparoAtual.setMensagemErro(e.getMessage());
            filaRepository.save(disparoAtual);
        }
    }
}