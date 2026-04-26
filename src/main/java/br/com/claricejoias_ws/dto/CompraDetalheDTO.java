package br.com.claricejoias_ws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompraDetalheDTO {

    private Long id;
    private LocalDateTime data;
    private BigDecimal total;
    private String metodoPagamento; // "especie", "pix", "cartao", "fiado"
    private BigDecimal valorEntrada;    // Pode ser null ou 0 se não for fiado
    private Integer parcelas;       // 1 para a vista, >1 para parcelado

    // Se quiser retornar os itens comprados no futuro, pode adicionar:
    // private List<ItemCompraDTO> itens;
}