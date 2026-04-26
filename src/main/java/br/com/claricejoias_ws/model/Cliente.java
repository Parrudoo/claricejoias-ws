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

    @Column(unique = true) // Evita duplicar o mesmo WhatsApp
    private String telefone;

    // NOVO: Campo necessário para a lógica de "dar baixa" funcionar
    @Column(name = "saldo_devedor", precision = 10, scale = 2)
    private BigDecimal saldoDevedor = BigDecimal.ZERO;

    // Inicializar as listas evita NullPointerException ao criar um novo cliente
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Venda> vendas = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HistoricoCobranca> historicoCobrancas = new ArrayList<>();

    // NOVO: Mapeamento para o histórico de pagamentos/baixas que criamos
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pagamento> pagamentos = new ArrayList<>();
}