package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ItemPedidoDTO {
    // Aqui você pode reaproveitar o seu ProdutoDTO que já existe
    private ProdutoDTO produto;
    private Integer quantidade;
    private BigDecimal precoUnitario; // O preço congelado na hora da compra
}