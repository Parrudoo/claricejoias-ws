package br.com.claricejoias_ws.dto;

import br.com.claricejoias_ws.enums.StatusPedido;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PedidoDTO {
    private Long id;
    private LocalDateTime dataCriacao;
    private StatusPedido statusPedido;
    private String formaPagamento;
    private BigDecimal totalCobrado;
    private List<ItemPedidoDTO> itens;
    private String loginOperador;
}