package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class Carrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Este é o identificador anônimo gerado pelo React (UUID)
    @Column(unique = true)
    private String visitorId;


     @Column(unique = true)
     private String usuarioId;

    @OneToMany(mappedBy = "carrinho", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCarrinho> itens = new ArrayList<>();


    // O Jackson (Spring) vai converter esse método automaticamente para um campo "valorTotal" no JSON
    public java.math.BigDecimal getValorTotal() {
        if (itens == null || itens.isEmpty()) {
            return java.math.BigDecimal.ZERO;
        }
        return itens.stream()
                .map(item -> item.getProduto().getPreco().multiply(java.math.BigDecimal.valueOf(item.getQuantidade())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}