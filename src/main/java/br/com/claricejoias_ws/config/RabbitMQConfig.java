package br.com.claricejoias_ws.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String FILA_DISPAROS = "whatsapp.disparos.queue";
    public static final String EXCHANGE_DISPAROS = "whatsapp.exchange";
    public static final String ROUTING_KEY_DISPAROS = "whatsapp.routing.key";

    public static final String DLQ_DISPAROS = "whatsapp.disparos.dlq";
    public static final String DLQ_EXCHANGE = "whatsapp.dlq.exchange";
    public static final String DLQ_ROUTING_KEY = "whatsapp.dlq.routing.key";

    // Fila Principal com redirecionamento para DLQ em caso de erro
    @Bean
    public Queue filaDisparos() {
        return QueueBuilder.durable(FILA_DISPAROS)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange exchangeDisparos() {
        return new DirectExchange(EXCHANGE_DISPAROS);
    }

    @Bean
    public Binding bindingDisparos(Queue filaDisparos, DirectExchange exchangeDisparos) {
        return BindingBuilder.bind(filaDisparos).to(exchangeDisparos).with(ROUTING_KEY_DISPAROS);
    }

    // Fila de Mensagens Mortas (Erros)
    @Bean
    public Queue dlqDisparos() {
        return QueueBuilder.durable(DLQ_DISPAROS).build();
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }

    @Bean
    public Binding dlqBinding(Queue dlqDisparos, DirectExchange dlqExchange) {
        return BindingBuilder.bind(dlqDisparos).to(dlqExchange).with(DLQ_ROUTING_KEY);
    }

    // Conversor para trafegar os dados em JSON no RabbitMQ
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}