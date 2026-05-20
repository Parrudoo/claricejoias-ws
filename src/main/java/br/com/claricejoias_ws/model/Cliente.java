package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "clientes")
public class Cliente {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String whatsapp;

    private String email;

    @OneToOne(mappedBy = "cliente")
    private Lead lead;

    @ManyToOne
    @JoinColumn(name = "revendedor_id")
    private Revendedor revendedor;

    @Column(unique = true, nullable = false)
    private String usuarioId;

    @Column(name = "saldo_devedor", precision = 10, scale = 2)
    private BigDecimal saldoDevedor = BigDecimal.ZERO;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pedido> pedidos = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HistoricoCobranca> historicoCobrancas = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pagamento> pagamentos = new ArrayList<>();

    // ========================================================================
    // ENCAPSULAMENTO DAS REGRAS DE NEGÓCIO (SOBRESCREVENDO O LOMBOK)
    // ========================================================================

    public void setWhatsapp(String whatsapp) {
        // Se vier nulo, guarda nulo. Se vier preenchido, limpa tudo que não for número.
        this.whatsapp = (whatsapp != null) ? whatsapp.replaceAll("[^0-9]", "") : null;
    }

}