package br.com.claricejoias_ws.dto;

import br.com.claricejoias_ws.model.Produto;
import lombok.Data;

@Data
public class LeadItemDTO {

    private Long id;
    private Integer quantidade;
    private Double precoMomento;
    private ProdutoDTO produto;
}
