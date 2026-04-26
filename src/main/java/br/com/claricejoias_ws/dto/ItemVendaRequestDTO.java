package br.com.claricejoias_ws.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemVendaRequestDTO {
    private Long id; // Refere-se ao ID do Produto
    private String nome;
    private BigDecimal preco;
    private Integer quantidade;
}