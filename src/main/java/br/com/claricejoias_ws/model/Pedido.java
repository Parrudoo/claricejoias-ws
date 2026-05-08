package br.com.claricejoias_ws.model;

import br.com.claricejoias_ws.enums.StatusPedido;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Rastreabilidade (igual ao carrinho)
    private String visitorId;
    private String usuarioId;

    // Vínculo com o cliente (Lead)
    @ManyToOne
    @JoinColumn(name = "lead_id")
    private Lead lead;

    // Dados da venda
    private BigDecimal totalCobrado;
    private String formaPagamento; // pix, cartao, negociar
    private StatusPedido statusPedido; // AGUARDANDO_WHATSAPP, CONCLUIDO, CANCELADO

    private LocalDateTime dataCriacao = LocalDateTime.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> itens = new ArrayList<>();

    public void addItem(ItemPedido item) {
        itens.add(item);
        item.setPedido(this);
    }
}