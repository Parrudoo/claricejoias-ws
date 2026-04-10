package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.util.List;

@Data
public class LeadRequestDTO {
    private String nome;
    private String whatsapp;
    private List<ItemCarrinhoDTO> carrinho;

    @Data
    public static class ItemCarrinhoDTO {
        private Long id;
        private String nome;
        private Integer quantidade;
        private Double preco;
    }
}