//package br.com.claricejoias_ws.model;
//
//import jakarta.persistence.*;
//import lombok.*;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Getter
//@Setter
//@NoArgsConstructor
//@AllArgsConstructor
//@EqualsAndHashCode(onlyExplicitlyIncluded = true)
//@Entity
//@Table(name = "vendas")
//public class Venda {
//
//    @EqualsAndHashCode.Include
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    private String loginOperador;
//
//    @Column(name = "data_venda", nullable = false)
//    private LocalDateTime dataVenda;
//
//    @Column(nullable = false)
//    private BigDecimal total;
//
//    @Column(name = "metodo_pagamento", nullable = false)
//    private String metodoPagamento; // pix, cartao, especie, fiado
//
//    private Integer parcelas;
//
//    @Column(name = "valor_recebido")
//    private BigDecimal valorRecebido;
//
//    private BigDecimal troco;
//
//    // CORREÇÃO 1: Trocado de Double para BigDecimal
//    @Column(name = "valor_entrada")
//    private BigDecimal valorEntrada = BigDecimal.ZERO;
//
//    @Column(name = "valor_devido")
//    private BigDecimal valorDevido = BigDecimal.ZERO;
//
//    // Relacionamento com os itens da venda
//    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<ItemVenda> itens = new ArrayList<>();
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "cliente_id")
//    private Cliente cliente;
//
//    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<Parcela> parcelasDetalhadas = new ArrayList<>();
//
//    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<Pagamento> pagamentos = new ArrayList<>();
//
//    /**
//     * Helper method para calcular o valor devido antes de salvar.
//     */
//    @PrePersist // 👇 CORREÇÃO 3: Removido o @PreUpdate para não estragar os pagamentos
//    public void calcularValores() {
//        if ("fiado".equalsIgnoreCase(this.metodoPagamento)) {
//            // 👇 CORREÇÃO 2: Matemática do BigDecimal do jeito certo
//            BigDecimal entrada = (this.valorEntrada != null) ? this.valorEntrada : BigDecimal.ZERO;
//            BigDecimal totalVenda = (this.total != null) ? this.total : BigDecimal.ZERO;
//
//            this.valorDevido = totalVenda.subtract(entrada);
//        } else {
//            this.valorDevido = BigDecimal.ZERO;
//        }
//    }
//}