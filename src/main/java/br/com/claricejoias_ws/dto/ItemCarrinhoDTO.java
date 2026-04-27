package br.com.claricejoias_ws.dto;

import lombok.Data;

@Data
public class ItemCarrinhoDTO {
    private Long id;
    private Integer quantidade;

    // Reutilizamos o seu ProdutoDTO que já está certinho e traz a lista de imagens!
    private ProdutoDTO produto;
}