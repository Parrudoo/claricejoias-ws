package br.com.claricejoias_ws.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class ItemCarrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private Integer quantidade;

    @ManyToOne
    @JoinColumn(name = "carrinho_id")
    @JsonIgnore // Evita loop infinito ao serializar para o React
    private Carrinho carrinho;
}