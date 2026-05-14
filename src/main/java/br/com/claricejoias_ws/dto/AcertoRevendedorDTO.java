package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class AcertoRevendedorDTO {
    private String revendedorId;
    private String revendedorNome;

    private Integer quantidadeVendas;
    private BigDecimal totalVendido;
    private BigDecimal lucroRevendedor; // O que fica com a revendedora
    private BigDecimal repasseMatriz;   // O que ela tem que transferir para a Clarice Joias

    // Opcional: Você pode incluir uma lista resumida das vendas para ela conferir
    // private List<PedidoResumoDTO> pedidosDetalhados;
}