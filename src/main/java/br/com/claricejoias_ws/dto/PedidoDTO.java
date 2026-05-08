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
    private StatusPedido statusPedido; // ex: AGUARDANDO_WHATSAPP, CONCLUIDO
    private String formaPagamento;
    private BigDecimal totalCobrado; // Total salvo no momento da compra
    private List<ItemPedidoDTO> itens;
}