package br.com.claricejoias_ws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MovimentacaoDTO {
    private String tipo; // "COMPRA" ou "PAGAMENTO"
    private LocalDateTime data;
    private Double valor;
    private String metodo;
    // Campos da sua Compra
    private Double valorEntrada;
    private Integer parcelas;
    // Campo do seu Pagamento
    private String observacao;
}
