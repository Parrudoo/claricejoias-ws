package br.com.claricejoias_ws.model;

import br.com.claricejoias_ws.enums.StatusParcela;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "parcelas")
public class Parcela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    private Integer numeroParcela;
    private BigDecimal valor;

    private LocalDate dataVencimento;
    private LocalDate dataPagamento; // Fica null até o cliente pagar

    @Enumerated(EnumType.STRING)
    private StatusParcela status;
}