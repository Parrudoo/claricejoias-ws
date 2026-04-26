package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "vendas")
public class Venda {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda;

    @Column(nullable = false)
    private Double total;

    @Column(name = "metodo_pagamento", nullable = false)
    private String metodoPagamento; // pix, cartao, especie, fiado

    private Integer parcelas;

    @Column(name = "valor_recebido")
    private Double valorRecebido;

    private Double troco;

    @Column(name = "valor_entrada")
    private Double valorEntrada = 0.0;

    @Column(name = "valor_devido")
    private Double valorDevido = 0.0;

    // Relacionamento com os itens da venda
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Parcela> parcelasDetalhadas = new ArrayList<>();

    /**
     * Helper method para calcular o valor devido antes de salvar.
     * Se for fiado, o devido é o total menos a entrada.
     * Se for outro método, o devido é zero.
     */
    @PrePersist
    @PreUpdate
    public void calcularValores() {
        if ("fiado".equalsIgnoreCase(this.metodoPagamento)) {
            double entrada = (this.valorEntrada != null) ? this.valorEntrada : 0.0;
            this.valorDevido = this.total - entrada;
        } else {
            this.valorDevido = 0.0;
        }
    }
}