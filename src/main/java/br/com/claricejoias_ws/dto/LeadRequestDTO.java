package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.util.List;

@Data
public class LeadRequestDTO {
    private String nome;
    private String whatsapp;
    private String email;
    private boolean criarConta;
    private String senha;
    private List<ItemCarrinhoDTO> itens;

    @Data
    public static class ItemCarrinhoDTO {
        private Long id;
        private String nome;
        private Integer quantidade;
        private String email;
        private Double preco;
    }
}