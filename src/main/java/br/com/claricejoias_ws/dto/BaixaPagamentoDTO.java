package br.com.claricejoias_ws.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BaixaPagamentoDTO(
        BigDecimal valorPago,
        String formaPagamento, // Ex: PIX, DINHEIRO, CARTAO
        LocalDate dataPagamento,
        String observacao,
        Long parcelaId
) {}