package br.com.claricejoias_ws.model;

import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@SQLDelete(sql = "UPDATE produto SET ativo = false WHERE id = ?")
@SQLRestriction("ativo = true")
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

    @Column(nullable = false)
    private boolean ativo = true; // Por padrão, o produto nasce ativo

    @ManyToOne
    @JoinColumn(name = "subcategoria_id")
    private Subcategoria subcategoria;

    public void adicionarImagem(String caminho) {
        this.imagens.add(caminho);
    }

    public void diminuirEstoque(Integer quantidade) {
        if (this.estoque < quantidade) {
            throw new RegraNegocioException("Estoque insuficiente para o produto: " + this.nome);
        }
        this.estoque -= quantidade;
    }

    public void adicionarEstoque(Integer quantidade) {
        this.estoque += quantidade;
    }
}