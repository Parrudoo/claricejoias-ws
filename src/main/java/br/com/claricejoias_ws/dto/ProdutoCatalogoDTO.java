package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProdutoCatalogoDTO {
    private Long id;
    private String nome;
    private String codigo;
    private BigDecimal preco;
    private List<String> imagens;

    // Dados de Categorização
    private Long subcategoriaId;
    private String subcategoriaNome;
    private Long categoriaId;
    private String categoriaNome;
}