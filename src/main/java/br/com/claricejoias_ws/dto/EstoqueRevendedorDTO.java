package br.com.claricejoias_ws.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EstoqueRevendedorDTO {

    private Long id;
    private ProdutoDTO produto;
    private Integer quantidade;
}
