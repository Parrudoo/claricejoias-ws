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
    @Bean
    public ItemReader<Lead> leadReader(LeadRepository repository) {
        return new RepositoryItemReaderBuilder<Lead>()
                .name("leadReader")
                .repository(repository)
                .methodName("findByAtivoTrueAndComprouFalse")
                .sorts(Map.of("id", Sort.Direction.ASC))
                .build();
    }

    // 2. PROCESSOR (Transforma o Lead numa Mensagem)
    @Bean
    public ItemProcessor<Lead, MensagemDTO> leadProcessor() {
        return lead -> {

            // Regra de Negócio: Não enviar mensagens para leads inativos ou que já compraram
            if (!lead.getAtivo() || lead.getComprou()) {
                return null; // O Spring Batch ignora automaticamente retornos nulos (pula para o próximo)
            }

            // Constrói o texto iterando sobre a nova lista de LeadItem
            StringBuilder resumoItens = new StringBuilder();
            if (lead.getItens() != null && !lead.getItens().isEmpty()) {
                lead.getItens().forEach(item -> {
                    resumoItens.append("🔸 ").append(item.getQuantidade()).append("x ");

                    // Valida se o produto existe para não quebrar o código
                    if (item.getProduto() != null) {
                        resumoItens.append(item.getProduto().getNome());
                    } else {
                        resumoItens.append("Joia Exclusiva");
                    }
                    resumoItens.append("\n");
                });
            } else {
                resumoItens.append("nossas novidades!\n");
            }

            String texto = "Olá " + lead.getNome() + "! Aqui é da Clarice Joias.\n" +
                    "Vimos que você separou algumas peças fantásticas recentemente:\n\n" +
                    resumoItens.toString() +
                    "\nTemos uma oferta especial liberada para você hoje. Gostaria de conferir?";

            String numeroCorreto = lead.getWhatsapp();
            if (numeroCorreto != null && !numeroCorreto.startsWith("55")) {
                numeroCorreto = "55" + numeroCorreto;
            }

            return new MensagemDTO(numeroCorreto, texto);
        };
    }

    // 3. WRITER (Envia efetivamente a Mensagem)
    @Bean
    public ItemWriter<MensagemDTO> leadWriter() {
        RestTemplate restTemplate = new RestTemplate();
        // Lembre-se de ajustar a URL para apontar para a máquina correta se estiver rodando o Docker no servidor
        String evolutionApiUrl = "http://localhost:8081/message/sendText/claricejoias";

        return mensagens -> {
            for (MensagemDTO msg : mensagens) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("apikey", "claricejoias");

                Map<String, Object> body = Map.of(
                        "number", msg.getNumero(),
                        "textMessage", Map.of("text", msg.getTexto())
                );

                try {
                    restTemplate.postForEntity(evolutionApiUrl, new HttpEntity<>(body, headers), String.class);
                    System.out.println("Mensagem enviada com sucesso para: " + msg.getNumero());

                    // Pausa de 30 segundos para evitar bloqueio do WhatsApp
                    Thread.sleep(30000);
                } catch (Exception e) {
                    System.err.println("Falha ao enviar para " + msg.getNumero());
                    System.err.println("Motivo do erro: " + e.getMessage());
                }
            }
        };
    }

    // 4. STEP (Junta as 3 partes)
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

    // Classe auxiliar
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