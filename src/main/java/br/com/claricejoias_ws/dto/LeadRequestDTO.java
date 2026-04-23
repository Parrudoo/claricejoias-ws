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
    private List<ItemRequestDTO> itens;

    // Classe interna pública e estática
    @Data
    public static class ItemRequestDTO {
        private Long id;
        private Integer quantidade;
        private Double preco;
    }
}