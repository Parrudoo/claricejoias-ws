package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.LeadDTO;
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
import org.modelmapper.ModelMapper;
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
@RequiredArgsConstructor
public class WhatsAppService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final HistoricoDisparoRepository historicoRepository;
    private final FilaDisparoRepository filaRepository;
    private final EvolutionApiService evolutionApiService;
    private final HistoricoCobrancaRepository historicoCobrancaRepository;
    private final FilaCobrancaRepository filaCobrancaRepository;
    private final MinioService minioService;

    @Value("${app.whatsapp.cooldown-horas:24}")
    private int cooldownHoras;

    @Value("${evolution.api.url}")
    private String evolutionApiUrl;

    @Value("${evolution.api.instance}")
    private String instancia;

    @Value("${evolution.api.key}")
    private String apiKey;

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.bucket-name}")
    private String bucketName;

    private final ModelMapper modelMapper;

    // =========================================================================
    // 1. ENFILEIRAR MENSAGEM PARA LEADS (TEXTO)
    // =========================================================================
    public void enviarMensagemTexto(LeadDTO lead, String texto, String operador) {
        if (filaRepository.existsByLeadIdAndStatus(lead.getId(), StatusDisparo.PENDENTE)) {
            throw new RegraNegocioException("Operação negada: " + lead.getNome() + " já possui uma mensagem na fila aguardando disparo.");
        }

        validarCooldown(modelMapper.map(lead,Lead.class));

        FilaDisparo fila = new FilaDisparo();
        fila.setLead(modelMapper.map(lead,Lead.class));
        fila.setTexto(texto);
        fila.setOperador(operador);
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());

        filaRepository.save(fila);
        System.out.println("Mensagem ENFILEIRADA para o lead: " + lead.getNome());
    }

    // =========================================================================
    // NOVO: 1.1 ENFILEIRAR IMAGEM PARA LEADS (MÍDIA)
    // =========================================================================
    public void enviarMensagemImagem(Lead lead, String legenda,String path, String operador) {
        FilaDisparo fila = new FilaDisparo();
        fila.setLead(lead);
        fila.setTexto(legenda); // A legenda vai no campo texto
        fila.setUrlImagem(path); // O novo campo que criamos na Entidade
        fila.setOperador(operador);
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());

        filaRepository.save(fila);
        System.out.println("IMAGEM ENFILEIRADA para o lead: " + lead.getNome());
    }

    private void validarCooldown(Lead lead) {
        Optional<HistoricoDisparo> ultimoDisparo = historicoRepository.findTopByLeadIdOrderByDataHoraDisparoDesc(lead.getId());
        if (ultimoDisparo.isPresent()) {
            LocalDateTime dataUltimo = ultimoDisparo.get().getDataHoraDisparo();
            LocalDateTime dataLiberacao = dataUltimo.plus(cooldownHoras, ChronoUnit.HOURS);

            if (LocalDateTime.now().isBefore(dataLiberacao)) {
                throw new RegraNegocioException("Aguarde! O próximo disparo para " + lead.getNome() +
                        " só estará liberado em: " + dataLiberacao);
            }
        }
    }

    // =========================================================================
    // 2. ENFILEIRAR MENSAGEM PARA CLIENTES (COBRANÇA)
    // =========================================================================
    public void enviarCobrancaCliente(Cliente cliente, String texto, String operador) {
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
    // 3. TRABALHADOR DE LEADS (MISTO: TEXTO E IMAGEM) - 15 SEGUNDOS
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

        if (numeroCorreto == null || numeroCorreto.trim().isEmpty()) {
            System.err.println("Fila FALHOU: O Lead (ID: " + lead.getId() + ") não possui número de WhatsApp válido.");
            disparoAtual.setStatus(StatusDisparo.ERRO);
            disparoAtual.setMensagemErro("Número de WhatsApp é nulo ou vazio.");
            filaRepository.save(disparoAtual);
            return;
        }

        if (!numeroCorreto.startsWith("55")) {
            numeroCorreto = "55" + numeroCorreto;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apiKey);

        Map<String, Object> body;
        String urlDestino;

        // VERIFICA SE O REGISTRO TEM IMAGEM
        if (disparoAtual.getUrlImagem() != null && !disparoAtual.getUrlImagem().isEmpty()) {

            urlDestino = evolutionApiUrl + "/message/sendMedia/" + instancia;
            String nomeArquivo = disparoAtual.getUrlImagem();

            // --- COPIE E COLE ESTE BLOCO DE LOG AQUI ---
            System.out.println("=========================================");
            System.out.println("-> Tentando baixar do MinIO");
            System.out.println("-> Arquivo procurado pelo Java: '" + nomeArquivo + "'");
            System.out.println("=========================================");
            // -------------------------------------------

            try {
                // A OPÇÃO NUCLEAR: Pega o Base64 direto do MinIO em vez de montar URL
                String mediaBase64 = minioService.getImagemBase64(nomeArquivo);

                body = Map.of(
                        "number", numeroCorreto,
                        "mediaMessage", Map.of(
                                "mediatype", "image",
                                "caption", disparoAtual.getTexto() != null ? disparoAtual.getTexto() : "",
                                "media", mediaBase64 // <- Agora a Evolution vai aceitar sem pestanejar!
                        )
                );
                System.out.println("Processando envio de IMAGEM (via Base64) para " + numeroCorreto);

            } catch (Exception e) {
                System.err.println("Fila FALHOU: Erro ao baixar imagem do MinIO: " + e.getMessage());

                disparoAtual.setStatus(StatusDisparo.ERRO);
                disparoAtual.setMensagemErro("Erro ao converter MinIO para Base64: " + e.getMessage());
                filaRepository.save(disparoAtual);
                return; // Interrompe o envio deste disparo
            }

        } else {
            // SE NÃO TIVER IMAGEM, ENVIA TEXTO NORMAL
            urlDestino = evolutionApiUrl + "/message/sendText/" + instancia;

            body = Map.of(
                    "number", numeroCorreto,
                    "textMessage", Map.of(
                            "text", disparoAtual.getTexto() != null ? disparoAtual.getTexto() : ""
                    )
            );
            System.out.println("Processando envio de TEXTO para " + numeroCorreto);
        }

        try {
            restTemplate.postForEntity(urlDestino, new HttpEntity<>(body, headers), String.class);
            System.out.println("Fila PROCESSADA com sucesso!");

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
    // 3.1 TRABALHADOR DE OTP - 15 SEGUNDOS
    // =========================================================================
    @Scheduled(fixedDelay = 15000)
    public void processarFila() {
        Optional<FilaDisparo> mensagemPendente = filaRepository
                .findFirstByTipoAndStatusOrderByDataCriacaoAsc("OTP", StatusDisparo.PENDENTE);

        if (mensagemPendente.isEmpty()) {
            return;
        }

        FilaDisparo fila = mensagemPendente.get();
        long minutosNaFila = ChronoUnit.MINUTES.between(fila.getDataCriacao(), LocalDateTime.now());

        if (minutosNaFila >= 5) {
            fila.setStatus(StatusDisparo.EXPIRADO);
            fila.setMotivoFalha("OTP expirou antes do envio (mais de 5 min na fila)");
            filaRepository.save(fila);
            System.out.println("OTP descartado, tempo expirado.");
            return;
        }

        try {
            evolutionApiService.enviarMensagemTexto(fila.getNumeroDestino(), fila.getTexto());
            fila.setStatus(StatusDisparo.ENVIADO);
            fila.setDataDisparo(LocalDateTime.now());
            System.out.println("OTP enviado com sucesso para: " + fila.getNumeroDestino());
        } catch (Exception e) {
            fila.setStatus(StatusDisparo.ERRO);
            System.err.println("Erro ao disparar OTP: " + e.getMessage());
        }
        filaRepository.save(fila);
    }

    // =========================================================================
    // 4. TRABALHADOR DE COBRANÇAS EM SEGUNDO PLANO - 20 SEGUNDOS
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
            String url = evolutionApiUrl + "/message/sendText/" + instancia;
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
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