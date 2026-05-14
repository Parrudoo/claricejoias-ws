package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class EstoqueRevendedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "revendedor_id", nullable = false)
    private Revendedor revendedor;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    private Integer quantidade;

    // Regra de negócio encapsulada
    public void diminuirEstoque(Integer quantidadeVendida) {
        if (this.quantidade == null || this.quantidade < quantidadeVendida) {
            throw new RuntimeException("Estoque insuficiente na maleta para o produto: " + produto.getNome());
        }
        this.quantidade -= quantidadeVendida;
    }
}