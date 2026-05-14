package br.com.claricejoias_ws.dto;

import lombok.Data;

@Data
public class TransferenciaEstoqueDTO {
    private Long produtoId;
    private String revendedorId;
    private Integer quantidade;
}