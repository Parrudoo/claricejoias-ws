package br.com.claricejoias_ws.batch;

import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.LeadRepository;
import br.com.claricejoias_ws.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;

@Configuration
@RequiredArgsConstructor
public class CampanhaBatchConfig {

    private final LeadRepository leadRepository;

    // 1. READER (Lê os Leads da Base de Dados)
    @Bean
    public ItemReader<Lead> leadReader(LeadRepository repository) {
        return new RepositoryItemReaderBuilder<Lead>()
                .name("leadReader")
                .repository(repository)
                .methodName("findByAtivoTrueAndComprouFalse")
                .pageSize(100)
                .sorts(Collections.singletonMap("id", Sort.Direction.ASC))
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

            // Constrói o texto iterando sobre a lista de LeadItem
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

            // A formatação do "+55" foi removida daqui, pois o WhatsAppService já faz isso!
            // Agora passamos o objeto Lead inteiro para o DTO
            return new MensagemDTO(lead, texto);
        };
    }

    // 3. WRITER (Envia efetivamente a Mensagem)
    @Bean
    public ItemWriter<MensagemDTO> leadWriter(WhatsAppService whatsAppService) {
        return mensagens -> {
            for (MensagemDTO msg : mensagens) {
                try {
                    // Chama o serviço passando a entidade Lead inteira
                    whatsAppService.enviarMensagemTexto(msg.getLead(), msg.getTexto(),"BATCH");

                    // Pausa de 30 segundos mantida APENAS para o processo em lote
                    Thread.sleep(30000);

                } catch (IllegalStateException e) {
                    // Captura a exceção de limite de tempo (cooldown) e avisa no log sem quebrar o batch
                    System.out.println("Batch pulou o lead " + msg.getLead().getNome() + " - " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Erro no Batch ao processar o lead: " + msg.getLead().getNome());
                    System.err.println("Motivo: " + e.getMessage());
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

    // Classe auxiliar atualizada para segurar a entidade Lead
    public static class MensagemDTO {
        private Lead lead;
        private String texto;

        public MensagemDTO(Lead lead, String texto) {
            this.lead = lead;
            this.texto = texto;
        }
        public Lead getLead() { return lead; }
        public String getTexto() { return texto; }
    }
}