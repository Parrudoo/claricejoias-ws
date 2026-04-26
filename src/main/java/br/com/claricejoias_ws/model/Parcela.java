package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "parcelas")
public class Parcela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venda_id")
    private Venda venda;

    private Integer numeroParcela; // 1, 2, 3...
    private Double valor;

    private LocalDate dataVencimento;
    private LocalDate dataPagamento; // Fica null até o cliente pagar

    private String status; // PENDENTE, PAGA, CANCELADA
}