package br.com.claricejoias_ws.dto;

import br.com.claricejoias_ws.enums.StatusParcela;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParcelaDTO {
    private Long id;
    private Integer numeroParcela;
    private BigDecimal valor;
    private LocalDate dataVencimento;
    private LocalDate dataPagamento;
    private StatusParcela status; // PENDENTE, PAGA, CANCELADA
}