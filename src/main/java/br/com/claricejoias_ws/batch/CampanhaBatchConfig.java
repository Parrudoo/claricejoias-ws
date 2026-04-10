package br.com.claricejoias_ws.batch;

import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.LeadRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Configuration
public class CampanhaBatchConfig {

    // 1. READER (Lê os Leads da Base de Dados)
    // Lê os dados em blocos (chunks) para não sobrecarregar a memória
    @Bean
    public ItemReader<Lead> leadReader(LeadRepository repository) {
        return new RepositoryItemReaderBuilder<Lead>()
                .name("leadReader")
                .repository(repository)
                .methodName("findAll") // Num cenário real, criaria um método como "findLeadsNaoContactados"
                .sorts(Map.of("id", Sort.Direction.ASC))
                .build();
    }

    // 2. PROCESSOR (Transforma o Lead numa Mensagem)
    // Prepara o texto personalizado para cada cliente
    @Bean
    public ItemProcessor<Lead, MensagemDTO> leadProcessor() {
        return lead -> {
            String texto = "Olá " + lead.getNome() + "! Aqui é da Clarice Joias. " +
                    "Ainda tem interesse nestas peças fantásticas?\n" + lead.getItensInteresse();

            String numeroCorreto = lead.getWhatsapp();
            // Se o número não começar com 55, nós adicionamos!
            if (!numeroCorreto.startsWith("55")) {
                numeroCorreto = "55" + numeroCorreto;
            }

            return new MensagemDTO(numeroCorreto, texto);
        };
    }

    // 3. WRITER (Envia efetivamente a Mensagem)
    // Faz a chamada à Evolution API
    @Bean
    public ItemWriter<MensagemDTO> leadWriter() {
        RestTemplate restTemplate = new RestTemplate();
        String evolutionApiUrl = "http://localhost:8081/message/sendText/claricejoias";

        return mensagens -> {
            for (MensagemDTO msg : mensagens) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("apikey", "claricejoias");

                // 1. Criamos a estrutura aninhada que a Evolution API v1.8.2 exige
                Map<String, Object> body = Map.of(
                        "number", msg.getNumero(),
                        "textMessage", Map.of("text", msg.getTexto()) // <-- A MUDANÇA ESTÁ AQUI
                );

                try {
                    restTemplate.postForEntity(evolutionApiUrl, new HttpEntity<>(body, headers), String.class);
                    System.out.println("Mensagem enviada com sucesso para: " + msg.getNumero());

                    // Pausa de 30 segundos entre mensagens para evitar bloqueio do WhatsApp!
                    Thread.sleep(30000);
                } catch (Exception e) {
                    System.err.println("Falha ao enviar para " + msg.getNumero());
                    System.err.println("Motivo do erro: " + e.getMessage());
                }
            }
        };
    }

    // 4. STEP (Junta as 3 partes)
    // Processa de 10 em 10 contactos
    @Bean
    public Step enviarMensagensStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                    ItemReader<Lead> reader, ItemProcessor<Lead, MensagemDTO> processor, ItemWriter<MensagemDTO> writer) {
        return new StepBuilder("enviarMensagensStep", jobRepository)
                .<Lead, MensagemDTO>chunk(10, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    // 5. JOB (A tarefa principal que será executada)
    @Bean
    public Job campanhaJob(JobRepository jobRepository, Step enviarMensagensStep) {
        return new JobBuilder("campanhaWhatsAppJob", jobRepository)
                .start(enviarMensagensStep)
                .build();
    }

    // Classe auxiliar para transportar os dados entre o Processor e o Writer
    public static class MensagemDTO {
        private String numero;
        private String texto;

        public MensagemDTO(String numero, String texto) {
            this.numero = numero;
            this.texto = texto;
        }
        public String getNumero() { return numero; }
        public String getTexto() { return texto; }
    }
}