package br.com.claricejoias_ws.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("estoque")
    private Integer quantidadeEstoqueCentral;
    private String codigo;
    private List<String> imagens;

    private String material;
}