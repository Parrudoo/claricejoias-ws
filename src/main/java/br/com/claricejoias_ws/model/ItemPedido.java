package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private Integer quantidade;

    private BigDecimal subtotal;


    // ️ ESSENCIAL: Salva o preço que o produto custava NA HORA QUE ELE CLICOU "ENVIAR PEDIDO"
    private BigDecimal precoUnitario;
}