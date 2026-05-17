package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.*;
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
    private String instanciaGlobal;

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
    public void enviarMensagemTexto(LeadDTO lead, String texto, String operador, Revendedor revendedor) {
        if (filaRepository.existsByLeadIdAndStatus(lead.getId(), StatusDisparo.PENDENTE)) {
            throw new RegraNegocioException("Operação negada: " + lead.getNome() + " já possui uma mensagem na fila aguardando disparo.");
        }

        validarCooldown(modelMapper.map(lead,Lead.class));

        FilaDisparo fila = new FilaDisparo();
        fila.setLead(modelMapper.map(lead,Lead.class));
        fila.setTexto(texto);
        fila.setRevendedorId(revendedor.getId());
        fila.setOperador(operador);
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());
        fila.setInstanciaWhatsapp(revendedor.getInstanciaWhatsapp() != null ? revendedor.getInstanciaWhatsapp() : instanciaGlobal);
        filaRepository.save(fila);
        System.out.println("Mensagem ENFILEIRADA para o lead: " + lead.getNome());
    }

    // =========================================================================
    // NOVO: 1.1 ENFILEIRAR IMAGEM PARA LEADS (MÍDIA)
    // =========================================================================
    public void enviarMensagemImagem(Lead lead, String legenda,String path, String operador, Revendedor revendedor) {
        FilaDisparo fila = new FilaDisparo();
        fila.setLead(lead);
        fila.setTexto(legenda); // A legenda vai no campo texto
        fila.setUrlImagem(path); // O novo campo que criamos na Entidade
        fila.setOperador(operador);
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());

        fila.setInstanciaWhatsapp(revendedor.getInstanciaWhatsapp() != null ? revendedor.getInstanciaWhatsapp() : instanciaGlobal);

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
    public void enviarCobrancaCliente(Cliente cliente, String texto, String operador, String instanciaRevendedor) {
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
        fila.setInstanciaWhatsapp(instanciaRevendedor != null ? instanciaRevendedor : instanciaGlobal);

        filaCobrancaRepository.save(fila);
        System.out.println("COBRANÇA ENFILEIRADA para o cliente: " + cliente.getNome());
    }

    // =========================================================================
    // 3. TRABALHADOR DE LEADS (MISTO: TEXTO E IMAGEM)
    // =========================================================================
    @Scheduled(fixedDelay = 15000)
    public void processarFilaDeDisparos() {
        Optional<FilaDisparo> disparoOptional = filaRepository.findFirstByStatusOrderByDataCriacaoAsc(StatusDisparo.PENDENTE);

        if (disparoOptional.isEmpty()) return;

        FilaDisparo disparoAtual = disparoOptional.get();
        Lead lead = disparoAtual.getLead();
        String numeroCorreto = lead.getWhatsapp();

        if (numeroCorreto == null || numeroCorreto.trim().isEmpty()) {
            disparoAtual.setStatus(StatusDisparo.ERRO);
            disparoAtual.setMensagemErro("WhatsApp inválido.");
            filaRepository.save(disparoAtual);
            return;
        }

        if (!numeroCorreto.startsWith("55")) numeroCorreto = "55" + numeroCorreto;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apiKey);

        Map<String, Object> body;
        String urlDestino;

        // RECUPERA A INSTÂNCIA DA FILA (Se for nula, usa a global)
        String instanciaParaUso = disparoAtual.getInstanciaWhatsapp() != null ? disparoAtual.getInstanciaWhatsapp() : instanciaGlobal;

        if (disparoAtual.getUrlImagem() != null && !disparoAtual.getUrlImagem().isEmpty()) {
            // MANDA PRA INSTÂNCIA DINÂMICA
            urlDestino = evolutionApiUrl + "/message/sendMedia/" + instanciaParaUso;

            try {
                String mediaBase64 = minioService.getImagemBase64(disparoAtual.getUrlImagem());
                body = Map.of(
                        "number", numeroCorreto,
                        "mediaMessage", Map.of(
                                "mediatype", "image",
                                "caption", disparoAtual.getTexto() != null ? disparoAtual.getTexto() : "",
                                "media", mediaBase64
                        )
                );
            } catch (Exception e) {
                disparoAtual.setStatus(StatusDisparo.ERRO);
                disparoAtual.setMensagemErro("Erro MinIO: " + e.getMessage());
                filaRepository.save(disparoAtual);
                return;
            }
        } else {
            // MANDA PRA INSTÂNCIA DINÂMICA
            urlDestino = evolutionApiUrl + "/message/sendText/" + instanciaParaUso;
            body = Map.of(
                    "number", numeroCorreto,
                    "textMessage", Map.of("text", disparoAtual.getTexto() != null ? disparoAtual.getTexto() : "")
            );
        }

        try {
            restTemplate.postForEntity(urlDestino, new HttpEntity<>(body, headers), String.class);
            disparoAtual.setStatus(StatusDisparo.ENVIADO);
            filaRepository.save(disparoAtual);

            historicoRepository.save(new HistoricoDisparo(lead, LocalDateTime.now(), disparoAtual.getOperador()));
        } catch (Exception e) {
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
    // 4. TRABALHADOR DE COBRANÇAS
    // =========================================================================
    @Scheduled(fixedDelay = 20000)
    public void processarFilaDeCobranca() {
        Optional<FilaCobranca> cobrancaOpt = filaCobrancaRepository.findFirstByStatusOrderByDataCriacaoAsc(StatusDisparo.PENDENTE);

        if (cobrancaOpt.isEmpty()) return;

        FilaCobranca cobranca = cobrancaOpt.get();
        Cliente cliente = cobranca.getCliente();
        String numeroCorreto = cliente.getWhatsapp().replaceAll("\\D", "");
        if (!numeroCorreto.startsWith("55")) numeroCorreto = "55" + numeroCorreto;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apiKey);

        Map<String, Object> body = Map.of(
                "number", numeroCorreto,
                "textMessage", Map.of("text", cobranca.getTexto())
        );

        // RECUPERA A INSTÂNCIA DA FILA
        String instanciaParaUso = cobranca.getInstanciaWhatsapp() != null ? cobranca.getInstanciaWhatsapp() : instanciaGlobal;
        String url = evolutionApiUrl + "/message/sendText/" + instanciaParaUso;

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            cobranca.setStatus(StatusDisparo.ENVIADO);
            filaCobrancaRepository.save(cobranca);

            HistoricoCobranca hist = new HistoricoCobranca();
            hist.setCliente(cliente);
            hist.setDataHora(LocalDateTime.now());
            hist.setFuncionario(cobranca.getOperador());
            historicoCobrancaRepository.save(hist);

        } catch (Exception e) {
            cobranca.setStatus(StatusDisparo.ERRO);
            cobranca.setMensagemErro(e.getMessage());
            filaCobrancaRepository.save(cobranca);
        }
    }
}