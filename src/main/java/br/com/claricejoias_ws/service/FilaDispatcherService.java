package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.config.RabbitMQConfig;
import br.com.claricejoias_ws.dto.DisparoMensagemDTO;
import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.model.FilaDisparo;
import br.com.claricejoias_ws.repository.FilaDisparoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilaDispatcherService {

    private final FilaDisparoRepository filaRepository;
    private final RabbitTemplate rabbitTemplate;

    // Roda a cada 5 segundos buscando novas mensagens no banco
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void despacharParaRabbitMQ() {
        List<FilaDisparo> mensagensJustas = filaRepository.findNextMessagesFairly();

        if (mensagensJustas.isEmpty()) {
            return;
        }

        log.info("Despachando {} mensagens balanceadas para o RabbitMQ...", mensagensJustas.size());

        for (FilaDisparo msg : mensagensJustas) {
            DisparoMensagemDTO dto = new DisparoMensagemDTO(
                    msg.getId(),            // 1. idRegistroBanco (Long)
                    "DISPARO",              // 2. tipoFila (String) - Pode ser "DISPARO" ou "COBRANCA"
                    msg.getTipo(),           // 3. tipoMensagem (String) - "TEXTO" ou "IMAGEM"
                    msg.getNumeroDestino(),          // 4. numeroDestino (String)
                    msg.getTexto(),         // 5. texto (String)
                    msg.getUrlImagem(),     // 6. urlImagem (String) - Pode ser null se for só texto
                    msg.getInstanciaWhatsapp()               // 7. instanciaWhatsapp (String)
            );

            // Envia para o RabbitMQ
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_DISPAROS, RabbitMQConfig.ROUTING_KEY_DISPAROS, dto);

            // Atualiza o status para não pegar no próximo loop
            msg.setStatus(StatusDisparo.ENVIADO);
            filaRepository.save(msg);
        }
    }
}