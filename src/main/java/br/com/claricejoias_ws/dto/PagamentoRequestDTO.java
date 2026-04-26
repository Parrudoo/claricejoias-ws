package br.com.claricejoias_ws.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PagamentoRequestDTO {
    private String metodo;
    private Integer parcelas;
    private BigDecimal valorRecebido;
    private BigDecimal valorEntrada; // <-- NOVO CAMPO ADICIONADO
}