package br.com.claricejoias_ws.dto;

import lombok.Data;

@Data
public class ProdutoDTO {
    private Long id;
    private String nome;
    private Double preco;
    private String pathImg; // Campo renomeado
    private String material;
}