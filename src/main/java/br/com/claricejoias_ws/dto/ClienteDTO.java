package br.com.claricejoias_ws.dto;

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
