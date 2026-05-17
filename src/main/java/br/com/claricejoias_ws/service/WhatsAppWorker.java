package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.config.RabbitMQConfig;
import br.com.claricejoias_ws.dto.DisparoMensagemDTO;
import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.repository.FilaDisparoRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppWorker {

    private final FilaDisparoRepository filaRepository;

    // ATENÇÃO: Injete aqui o seu serviço real da Evolution API que não veio nos arquivos
     private final EvolutionApiService evolutionApiService;

    @RabbitListener(queues = RabbitMQConfig.FILA_DISPAROS, ackMode = "MANUAL")
    public void processarDisparo(DisparoMensagemDTO mensagem, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        log.info("Processando mensagem ID: {} para o telefone: {}", mensagem.getIdRegistroBanco(), mensagem.getNumeroDestino());

        try {
            // ========================================================
            // AQUI VOCÊ CHAMA O SEU SERVIÇO DA EVOLUTION API
             boolean sucesso = evolutionApiService.enviarMensagemTexto(mensagem.getNumeroDestino(), mensagem.getTexto(),mensagem.getInstanciaWhatsapp());

            if (sucesso) {
                // Confirma que a mensagem foi processada (Remove da fila do RabbitMQ)
                channel.basicAck(tag, false);
                atualizarStatusBanco(mensagem.getIdRegistroBanco(), StatusDisparo.ENVIADO);

                // Proteção contra bloqueio do WhatsApp (Rate Limiting de 2 segundos)
                Thread.sleep(2000);
            } else {
                // Falha de envio por erro da API/Número (Não volta pra fila, vai pra ERRO no banco)
                channel.basicReject(tag, false);
                atualizarStatusBanco(mensagem.getIdRegistroBanco(), StatusDisparo.ERRO);
            }

        } catch (Exception e) {
            log.error("Falha na infraestrutura ao enviar mensagem ID: {}", mensagem.getNumeroDestino(), e);

            // Rejeita a mensagem e manda para a DLQ (Dead Letter Queue) para avaliar/retentar depois
            channel.basicNack(tag, false, false);
            atualizarStatusBanco(mensagem.getIdRegistroBanco(), StatusDisparo.FALHA_INFRA);
        }
    }

    private void atualizarStatusBanco(Long id, StatusDisparo status) {
        filaRepository.findById(id).ifPresent(fila -> {
            fila.setStatus(status);
            filaRepository.save(fila);
        });
    }
}