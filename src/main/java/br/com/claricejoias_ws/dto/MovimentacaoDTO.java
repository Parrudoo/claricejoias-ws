package br.com.claricejoias_ws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MovimentacaoDTO {
    private String tipo; // "COMPRA" ou "PAGAMENTO"
    private LocalDateTime data;
    private BigDecimal valor;
    private String metodo;
    private BigDecimal valorEntrada;
    private BigDecimal valorDevido;
    private List<ParcelaDTO> parcelas;

    private Integer qtdParcelas;
    // Campo do seu Pagamento
    private String observacao;
    private List<PagamentoDTO> historicoPagamentos;
}