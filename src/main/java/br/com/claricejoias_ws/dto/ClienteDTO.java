package br.com.claricejoias_ws.dto;

import br.com.claricejoias_ws.model.HistoricoCobranca;
import br.com.claricejoias_ws.model.Pagamento;
import br.com.claricejoias_ws.model.Venda;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ClienteDTO {

    private Long id;
    private String nome;
    private String whatsapp;
    private String email;
    private String usuarioId;
    private BigDecimal saldoDevedor = BigDecimal.ZERO;
    private List<HistoricoCobrancaDTO> historicoCobrancas;
    private List<PagamentoDTO> pagamentos;
}
