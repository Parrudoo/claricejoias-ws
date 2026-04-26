package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dataVenda;
    private Double total;

    private String metodoPagamento; // pix, cartao, especie
    private Integer parcelas;
    private Double valorRecebido;
    private Double troco;

    // Relacionamento com os itens da venda
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL)
    private List<ItemVenda> itens = new ArrayList<>();

    private Double valorEntrada; // Valor pago no ato da venda fiada
    private Double valorDevido;  // Total - Entrada (o que o robô vai cobrar)

    @ManyToOne(cascade = CascadeType.MERGE)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL)
    private List<Parcela> parcelasDetalhadas;
}