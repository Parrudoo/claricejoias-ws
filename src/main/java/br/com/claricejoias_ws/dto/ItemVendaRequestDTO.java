package br.com.claricejoias_ws.dto;

import lombok.Data;

@Data
public class ItemVendaRequestDTO {
    private Long id; // Refere-se ao ID do Produto
    private String nome;
    private Double preco;
    private Integer quantidade;
}