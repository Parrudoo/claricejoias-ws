package br.com.claricejoias_ws.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagamentoDTO {
    private LocalDateTime data;
    private BigDecimal valor;
    private String metodo;
    private String observacao;
}
