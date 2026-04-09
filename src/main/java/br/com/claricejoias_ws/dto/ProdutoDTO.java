package br.com.claricejoias_ws.dto;

import br.com.claricejoias_ws.model.Subcategoria;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Data
public class ProdutoDTO {

    private Long id;
    private String nome;
    private Double preco;
    private String img;
    private SubcategoriaDTO subcategoria;
    private String material;


}
