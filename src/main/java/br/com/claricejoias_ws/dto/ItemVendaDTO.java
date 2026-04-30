package br.com.claricejoias_ws.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ItemVendaDTO {

    private Long id;
    private Integer quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;
    private ProdutoDTO produto;
}
