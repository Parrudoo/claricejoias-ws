package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CheckoutDTO {
    // Dados do Cliente
    private String nome;
    private String whatsapp;
    private String email;

    // Dados do Pagamento (com valores padrão para compras via site/whatsapp)
    private String metodoPagamento = "whatsapp";
    private Integer parcelas = 1;
    private BigDecimal valorRecebido;
    private BigDecimal valorEntrada;
}