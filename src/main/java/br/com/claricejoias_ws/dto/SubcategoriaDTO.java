package br.com.claricejoias_ws.dto;

import br.com.claricejoias_ws.model.Categoria;
import br.com.claricejoias_ws.model.Produto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Data;

import java.util.List;

@Data
public class SubcategoriaDTO {

    private Long id;
    private String nome;
//    private List<ProdutoDTO> itens;
    private CategoriaDTO categoria;

}
