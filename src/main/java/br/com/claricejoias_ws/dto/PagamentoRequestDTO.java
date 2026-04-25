package br.com.claricejoias_ws.dto;

import lombok.Data;

@Data
public class PagamentoRequestDTO {
    private String metodo;
    private Integer parcelas;
    private Double valorRecebido;
}