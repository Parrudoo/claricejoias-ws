package br.com.claricejoias_ws.dto;

import br.com.claricejoias_ws.enums.StatusParcela;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ClienteResponseDTO {
    private Long id;
    private String nome;
    private String telefone;

    // Totalizador geral da dívida do cliente
    private BigDecimal valorDevido;

    private UltimaCobrancaDTO ultimaCobranca;

    // AQUI: A lista de vendas deste cliente
    private List<VendaResponseDTO> vendas;

    @Data
    public static class UltimaCobrancaDTO {
        private LocalDateTime dataHora;
        private String funcionario;
    }

    //  NOVAS ESTRUTURAS PARA DESCER O NÍVEL ATÉ A PARCELA
    @Data
    public static class VendaResponseDTO {
        private Long id;
        private LocalDateTime dataVenda;
        private BigDecimal total;
        private String metodoPagamento;
        private BigDecimal valorEntrada;
        private BigDecimal valorDevido; // O que ainda falta pagar DESTA venda

        // AQUI: As parcelas detalhadas desta venda específica
        private List<ParcelaResponseDTO> parcelas;
    }

    @Data
    public static class ParcelaResponseDTO {
        private Long id;
        private Integer numeroParcela;
        private BigDecimal valor;
        private LocalDate dataVencimento;
        private LocalDate dataPagamento;
        private StatusParcela status; // PENDENTE, PAGA
    }
}