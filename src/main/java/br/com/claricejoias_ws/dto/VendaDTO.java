package br.com.claricejoias_ws.dto;

import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.model.ItemVenda;
import br.com.claricejoias_ws.model.Pagamento;
import br.com.claricejoias_ws.model.Parcela;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class VendaDTO {


    private Long id;
    private LocalDateTime dataVenda;
    private BigDecimal total;
    private String metodoPagamento;
    private Integer parcelas;
    private BigDecimal valorRecebido;
    private BigDecimal troco;
    private BigDecimal valorEntrada;
    private BigDecimal valorDevido;
    private List<ItemVendaDTO> itens;
    private ClienteDTO cliente;
    private List<ParcelaDTO> parcelasDetalhadas;
    private List<PagamentoDTO> pagamentos;
    private String loginOperador;
}
