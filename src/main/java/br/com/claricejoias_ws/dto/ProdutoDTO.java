package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.util.List;

@Data
public class ProdutoDTO {
    private Long id;
    private String nome;
    private Double preco;

    // Campo atualizado para retornar a lista de links gerados pelo MinIO
    private List<String> imagens;

    private String material;
}