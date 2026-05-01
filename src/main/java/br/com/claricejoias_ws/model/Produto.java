package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private BigDecimal preco;
    private BigDecimal precoCusto;
    private Integer estoque;
    private String codigo;

    @Column(name = "rascunho")
    private boolean rascunho = true;

    // Campo atualizado para suportar uma lista de imagens
    @ElementCollection
    @CollectionTable(name = "produto_imagens", joinColumns = @JoinColumn(name = "produto_id"))
    @Column(name = "caminho_imagem")
    private List<String> imagens = new ArrayList<>();

    private String material;

    private String loginUsuario;

    @ManyToOne
    @JoinColumn(name = "subcategoria_id")
    private Subcategoria subcategoria;

    public void adicionarImagem(String caminho) {
        this.imagens.add(caminho);
    }
}