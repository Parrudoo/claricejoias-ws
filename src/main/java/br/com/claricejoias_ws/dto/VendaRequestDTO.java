package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.util.List;

@Data
public class VendaRequestDTO {
    private List<ItemVendaRequestDTO> itens;
    private Double total;
    private PagamentoRequestDTO pagamento;
    private ClienteRequestDTO cliente;
}