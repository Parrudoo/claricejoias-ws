package br.com.claricejoias_ws.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PedidoRequestDTO {
    private List<ItemVendaRequestDTO> itens;
    private BigDecimal total;
    private PagamentoRequestDTO pagamento;
    private ClienteRequestDTO cliente;
}