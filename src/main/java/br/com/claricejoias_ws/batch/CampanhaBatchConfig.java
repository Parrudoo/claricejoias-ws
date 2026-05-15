package br.com.claricejoias_ws.batch;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.LeadRepository;
import br.com.claricejoias_ws.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
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
    private final ModelMapper modelMapper;

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
                return null; // O Spring Batch ignora automaticamente retornos nulos
            }

            StringBuilder resumoItens = new StringBuilder();
            boolean temItens = false;

            // NOVA LÓGICA: Busca os itens dentro dos Pedidos (Carrinhos) do Lead
            if (lead.getPedidos() != null && !lead.getPedidos().isEmpty()) {
                lead.getPedidos().stream()
                        // Filtra apenas os pedidos que representam carrinhos não finalizados
                        .filter(pedido -> pedido.getStatus() != null &&
                                (pedido.getStatus().equals(StatusPedido.CARRINHO) || pedido.getStatus().equals(StatusPedido.CARRINHO_ABANDONADO)))
                        // Pega a lista de itens de cada carrinho encontrado e "achata" (flatMap) em um único fluxo
                        .flatMap(pedido -> pedido.getItens().stream())
                        .forEach(item -> {
                            resumoItens.append("🔸 ").append(item.getQuantidade()).append("x ");

                            // Valida se o produto existe para não quebrar o código
                            if (item.getProduto() != null) {
                                resumoItens.append(item.getProduto().getNome());
                            } else {
                                resumoItens.append("Joia Exclusiva");
                            }
                            resumoItens.append("\n");
                        });

                temItens = resumoItens.length() > 0;
            }

            // Se por acaso o lead se cadastrou mas nem chegou a colocar nada no carrinho
            if (!temItens) {
                resumoItens.append("nossas novidades!\n");
            }

            String texto = "Olá " + lead.getNome() + "! Aqui é da Clarice Joias.\n" +
                    "Vimos que você separou algumas peças fantásticas recentemente:\n\n" +
                    resumoItens.toString() +
                    "\nTemos uma oferta especial liberada para você hoje. Gostaria de conferir?";

            return new MensagemDTO(lead, texto);
        };
    }

    // 3. WRITER (Envia efetivamente a Mensagem)
    @Bean
    public ItemWriter<MensagemDTO> leadWriter(WhatsAppService whatsAppService) {
        return mensagens -> {
            for (MensagemDTO msg : mensagens) {
                try {
                    // Chama o serviço passando a entidade Lead convertida em DTO
                    whatsAppService.enviarMensagemTexto(modelMapper.map(msg.getLead(), LeadDTO.class) , msg.getTexto(),"BATCH",null);

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