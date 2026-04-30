package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.model.FilaCobranca;
import br.com.claricejoias_ws.model.FilaDisparo;
import br.com.claricejoias_ws.model.HistoricoCobranca;
import br.com.claricejoias_ws.model.HistoricoDisparo;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.FilaCobrancaRepository;
import br.com.claricejoias_ws.repository.FilaDisparoRepository;
import br.com.claricejoias_ws.repository.HistoricoCobrancaRepository;
import br.com.claricejoias_ws.repository.HistoricoDisparoRepository;
import lombok.RequiredArgsConstructor;
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

    // Repositórios para Leads (Marketing)
    private final HistoricoDisparoRepository historicoRepository;
    private final FilaDisparoRepository filaRepository;

    // Repositórios para Clientes (Cobrança e Relacionamento)
    private final HistoricoCobrancaRepository historicoCobrancaRepository;
    private final FilaCobrancaRepository filaCobrancaRepository;

    @Value("${app.whatsapp.cooldown-horas:24}")
    private int cooldownHoras;

    private final String evolutionApiUrl = "http://localhost:8081/message/sendText/claricejoias";
    private final String apiKey = "claricejoias";

    public WhatsAppService(HistoricoDisparoRepository historicoRepository,
                           FilaDisparoRepository filaRepository,
                           HistoricoCobrancaRepository historicoCobrancaRepository,
                           FilaCobrancaRepository filaCobrancaRepository) {
        this.restTemplate = new RestTemplate();
        this.historicoRepository = historicoRepository;
        this.filaRepository = filaRepository;
        this.historicoCobrancaRepository = historicoCobrancaRepository;
        this.filaCobrancaRepository = filaCobrancaRepository;
    }

    // =========================================================================
    // 1. ENFILEIRAR MENSAGEM PARA LEADS (Aba de Leads / Marketing)
    // =========================================================================
    public void enviarMensagemTexto(Lead lead, String texto, String operador) {

        if (filaRepository.existsByLeadIdAndStatus(lead.getId(), StatusDisparo.PENDENTE)) {
            throw new RegraNegocioException("Operação negada: " + lead.getNome() + " já possui uma mensagem na fila aguardando disparo.");
        }

        Optional<HistoricoDisparo> ultimoDisparo = historicoRepository.findTopByLeadIdOrderByDataHoraDisparoDesc(lead.getId());

        if (ultimoDisparo.isPresent()) {
            LocalDateTime dataUltimo = ultimoDisparo.get().getDataHoraDisparo();
            LocalDateTime dataLiberacao = dataUltimo.plus(cooldownHoras, ChronoUnit.HOURS);

            if (LocalDateTime.now().isBefore(dataLiberacao)) {
                throw new RegraNegocioException("Aguarde! O próximo disparo para " + lead.getNome() +
                        " só estará liberado em: " + dataLiberacao);
            }
        }

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
    // 2. ENFILEIRAR MENSAGEM PARA CLIENTES (Cobranças ou Promoções para quem já comprou)
    // =========================================================================
    public void enviarCobrancaCliente(Cliente cliente, String texto, String operador) {

        // Usa as tabelas exclusivas do CLIENTE (FilaCobranca e HistoricoCobranca)
        if (filaCobrancaRepository.existsByClienteIdAndStatus(cliente.getId(), StatusDisparo.PENDENTE)) {
            throw new RegraNegocioException("Já existe uma cobrança na fila para " + cliente.getNome());
        }

        Optional<HistoricoCobranca> ultimoHistorico = historicoCobrancaRepository
                .findFirstByClienteIdOrderByDataHoraDesc(cliente.getId());

        if (ultimoHistorico.isPresent()) {
            LocalDateTime dataLiberacao = ultimoHistorico.get().getDataHora().plus(cooldownHoras, ChronoUnit.HOURS);
            if (LocalDateTime.now().isBefore(dataLiberacao)) {
                throw new RegraNegocioException("Atenção! Este cliente já foi cobrado recentemente. " +
                        "Nova mensagem liberada em: " + dataLiberacao);
            }
        }

        FilaCobranca fila = new FilaCobranca();
        fila.setCliente(cliente);
        fila.setTexto(texto);
        fila.setOperador(operador);
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());

        filaCobrancaRepository.save(fila);
        System.out.println("COBRANÇA ENFILEIRADA para o cliente: " + cliente.getNome());
    }

    // =========================================================================
    // 3. TRABALHADOR DE LEADS EM SEGUNDO PLANO (Executa a cada 15 segundos)
    // =========================================================================
    @Scheduled(fixedDelay = 15000)
    public void processarFilaDeDisparos() {

        Optional<FilaDisparo> disparoOptional = filaRepository.findFirstByStatusOrderByDataCriacaoAsc(StatusDisparo.PENDENTE);

        if (disparoOptional.isEmpty()) {
            return;
        }

        FilaDisparo disparoAtual = disparoOptional.get();
        Lead lead = disparoAtual.getLead();

        String numeroCorreto = lead.getWhatsapp();
        if (numeroCorreto != null && !numeroCorreto.startsWith("55")) {
            numeroCorreto = "55" + numeroCorreto;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apiKey);

        Map<String, Object> body = Map.of(
                "number", numeroCorreto,
                "textMessage", Map.of("text", disparoAtual.getTexto())
        );

        try {
            restTemplate.postForEntity(evolutionApiUrl, new HttpEntity<>(body, headers), String.class);
            System.out.println("Fila PROCESSADA: Mensagem enviada via Evolution para " + numeroCorreto);

            disparoAtual.setStatus(StatusDisparo.ENVIADO);
            filaRepository.save(disparoAtual);

            HistoricoDisparo novoHistorico = new HistoricoDisparo(lead, LocalDateTime.now(), disparoAtual.getOperador());
            historicoRepository.save(novoHistorico);

        } catch (Exception e) {
            System.err.println("Fila FALHOU: Erro ao enviar para " + numeroCorreto);
            System.err.println("Motivo: " + e.getMessage());

            disparoAtual.setStatus(StatusDisparo.ERRO);
            disparoAtual.setMensagemErro(e.getMessage());
            filaRepository.save(disparoAtual);
        }
    }

    // =========================================================================
    // 4. TRABALHADOR DE COBRANÇAS EM SEGUNDO PLANO (Executa a cada 20 segundos)
    // =========================================================================
    @Scheduled(fixedDelay = 20000)
    public void processarFilaDeCobranca() {

        Optional<FilaCobranca> cobrancaOpt = filaCobrancaRepository.findFirstByStatusOrderByDataCriacaoAsc(StatusDisparo.PENDENTE);

        if (cobrancaOpt.isEmpty()) {
            return;
        }

        FilaCobranca cobranca = cobrancaOpt.get();
        Cliente cliente = cobranca.getCliente();

        String numeroCorreto = cliente.getWhatsapp().replaceAll("\\D", "");
        if (!numeroCorreto.startsWith("55")) {
            numeroCorreto = "55" + numeroCorreto;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apiKey);

        Map<String, Object> body = Map.of(
                "number", numeroCorreto,
                "textMessage", Map.of("text", cobranca.getTexto())
        );

        try {
            restTemplate.postForEntity(evolutionApiUrl, new HttpEntity<>(body, headers), String.class);
            System.out.println("Fila PROCESSADA: Cobrança enviada via Evolution para " + numeroCorreto);

            cobranca.setStatus(StatusDisparo.ENVIADO);
            filaCobrancaRepository.save(cobranca);

            HistoricoCobranca hist = new HistoricoCobranca();
            hist.setCliente(cliente);
            hist.setDataHora(LocalDateTime.now());
            hist.setFuncionario(cobranca.getOperador());
            historicoCobrancaRepository.save(hist);

        } catch (Exception e) {
            System.err.println("Fila de Cobrança FALHOU: Erro ao enviar para " + numeroCorreto);
            System.err.println("Motivo: " + e.getMessage());

            cobranca.setStatus(StatusDisparo.ERRO);
            cobranca.setMensagemErro(e.getMessage());
            filaCobrancaRepository.save(cobranca);
        }
    }
}