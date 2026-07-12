package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.config.RabbitMQConfig;
import br.com.claricejoias_ws.dto.DisparoMensagemDTO;
import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.*;
import br.com.claricejoias_ws.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private final HistoricoDisparoRepository historicoRepository;
    private final FilaDisparoRepository filaRepository;
    private final HistoricoCobrancaRepository historicoCobrancaRepository;
    private final FilaCobrancaRepository filaCobrancaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ModelMapper modelMapper;
    private final WhatsAppRepository whatsAppRepository;

    @Value("${app.whatsapp.cooldown-horas:24}")
    private int cooldownHoras;

    @Value("${evolution.api.instance}")
    private String instanciaGlobal;

    // =========================================================================
    // 1. MÉTODOS DE ENFILEIRAMENTO (SALVAM NO BANCO COM STATUS PENDENTE)
    // =========================================================================

    public void enviarMensagemTexto(LeadDTO lead, String texto, String operador, Revendedor revendedor) {
        if (filaRepository.existsByLeadIdAndStatus(lead.getId(), StatusDisparo.PENDENTE)) {
            throw new RegraNegocioException("Operação negada: " + lead.getNome() + " já possui uma mensagem na fila aguardando disparo.");
        }

        validarCooldown(modelMapper.map(lead, Lead.class));

        FilaDisparo fila = new FilaDisparo();
        fila.setLead(modelMapper.map(lead, Lead.class));
        fila.setTexto(texto);
        fila.setRevendedorId(revendedor != null ? revendedor.getId() : null);
        fila.setOperador(operador);
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());
        fila.setInstanciaWhatsapp(instanciaParaRevendedor(revendedor));

        filaRepository.save(fila);
        log.info("Mensagem TEXTO enfileirada para o lead: {}", lead.getNome());
    }

    public void enviarMensagemImagem(Lead lead, String legenda, String path, String operador, Revendedor revendedor) {
        // Nota: não aplicamos aqui a checagem de "já existe mensagem PENDENTE" que existe em
        // enviarMensagemTexto, porque o MensagemController enfileira várias imagens em sequência
        // para o mesmo lead numa única chamada (uma por produto do carrinho) — todas ficam
        // PENDENTE até o dispatcher rodar. Aplicar aquele check aqui quebraria esse fluxo.
        // O cooldown de 24h (validarCooldown) já impede o abuso entre disparos diferentes.
        validarCooldown(lead);

        FilaDisparo fila = new FilaDisparo();
        fila.setLead(lead);
        fila.setTexto(legenda);
        fila.setUrlImagem(path);
        fila.setRevendedorId(revendedor != null ? revendedor.getId() : null);
        fila.setOperador(operador);
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());
        fila.setInstanciaWhatsapp(instanciaParaRevendedor(revendedor));

        filaRepository.save(fila);
        log.info("Mensagem IMAGEM enfileirada para o lead: {}", lead.getNome());
    }

    private String instanciaParaRevendedor(Revendedor revendedor) {
        if (revendedor == null) {
            return instanciaGlobal;
        }
        return revendedor.getWhatsappInstance() != null ? revendedor.getWhatsappInstance().getInstanceName() : instanciaGlobal;
    }

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
        log.info("COBRANÇA enfileirada para o cliente: {}", cliente.getNome());
    }

    public void enfileirarMensagemSistema(String numeroDestino, String texto, Revendedor revendedor) {
        FilaDisparo fila = new FilaDisparo();
        fila.setNumeroDestino(numeroDestino);
        fila.setTexto(texto);
        fila.setTipo("OTP");
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());

        fila.setRevendedorId(revendedor != null ? revendedor.getId() : null);
        fila.setInstanciaWhatsapp(instanciaParaRevendedor(revendedor));

        filaRepository.save(fila);
        log.info("Mensagem OTP enfileirada para o número: {}", numeroDestino);
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
    // 2. DISPATCHERS (BUSCAM DO BANCO E JOGAM NO RABBITMQ)
    // =========================================================================

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void despacharFilaPrincipalParaRabbitMQ() {
        List<FilaDisparo> pendentes = filaRepository.findNextMessagesFairly();

        for (FilaDisparo disparo : pendentes) {

            // Tratamento extra para OTP expirado na hora de jogar pro Rabbit
            if ("OTP".equals(disparo.getTipo())) {
                long minutosNaFila = ChronoUnit.MINUTES.between(disparo.getDataCriacao(), LocalDateTime.now());
                if (minutosNaFila >= 5) {
                    disparo.setStatus(StatusDisparo.EXPIRADO);
                    disparo.setMotivoFalha("OTP expirou antes do envio (mais de 5 min na fila)");
                    filaRepository.save(disparo);
                    log.warn("OTP descartado pois expirou: {}", disparo.getNumeroDestino());
                    continue; // Pula pro próximo
                }
            }

            String tipoMensagem = (disparo.getUrlImagem() != null && !disparo.getUrlImagem().isEmpty()) ? "IMAGEM" : "TEXTO";
            String instancia = disparo.getInstanciaWhatsapp() != null ? disparo.getInstanciaWhatsapp() : instanciaGlobal;
            String numero = disparo.getLead() != null ? disparo.getLead().getWhatsapp() : disparo.getNumeroDestino();

            DisparoMensagemDTO dto = new DisparoMensagemDTO(
                    disparo.getId(), "DISPARO", tipoMensagem, numero,
                    disparo.getTexto(), disparo.getUrlImagem(), instancia
            );

            // Joga na Fila do RabbitMQ
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_DISPAROS, RabbitMQConfig.ROUTING_KEY_DISPAROS, dto);

            // Marca como processando para não pegar novamente no próximo loop do banco
            disparo.setStatus(StatusDisparo.EM_PROCESSAMENTO);
            filaRepository.save(disparo);
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void despacharCobrancasParaRabbitMQ() {
        // NÃO IMPLEMENTADO DE PROPÓSITO: hoje não existe fila/exchange nem consumidor dedicado
        // para FilaCobranca no RabbitMQConfig/WhatsAppWorker. Publicar essas mensagens na mesma
        // fila dos disparos (FILA_DISPAROS) faria o WhatsAppWorker tentar atualizar o status
        // usando FilaDisparoRepository.findById(id) — um id de FilaCobranca não existe (ou pior,
        // colide com o de outro registro) nessa tabela, corrompendo o status de disparos.
        // As cobranças enfileiradas em enviarCobrancaCliente ficam paradas em FilaCobranca até
        // que uma fila/consumidor próprios sejam criados para elas.
        if (filaCobrancaRepository.findFirstByStatusOrderByDataCriacaoAsc(StatusDisparo.PENDENTE).isPresent()) {
            log.warn("Há cobranças PENDENTES em FilaCobranca aguardando um consumidor dedicado no RabbitMQ (ainda não implementado).");
        }
    }



    public WhatsappInstance findByRevendedorIsNull() {
        return whatsAppRepository.findByRevendedorIsNull();
    }
}