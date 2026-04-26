package br.com.claricejoias_ws.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PagamentoDTO {
    private LocalDateTime data;
    private BigDecimal valor;
    private String metodo;
    private String observacao;
}
