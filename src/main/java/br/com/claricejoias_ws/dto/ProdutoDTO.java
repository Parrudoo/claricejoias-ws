package br.com.claricejoias_ws.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProdutoDTO {

    private Long id;
    private String nome;
    private BigDecimal preco;
    private BigDecimal precoCusto;
    private Integer estoque;
    private String codigo;
    // Campo atualizado para retornar a lista de links gerados pelo MinIO
    private List<String> imagens;

    private String material;
}