package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CarrinhoDTO {
    private Long id;
    private String visitorId;
    private BigDecimal valorTotal;

    // Lista de itens já tipada com o DTO para quebrar o loop do Hibernate
    private List<ItemCarrinhoDTO> itens;
}