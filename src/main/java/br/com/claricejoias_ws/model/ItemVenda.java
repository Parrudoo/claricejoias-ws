//package br.com.claricejoias_ws.model;
//
//import com.fasterxml.jackson.annotation.JsonIgnore;
//import jakarta.persistence.*;
//import lombok.Data;
//
//import java.math.BigDecimal;
//
//@Data
//@Entity
//public class ItemVenda {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    private Integer quantidade;
//    private BigDecimal precoUnitario;
//    private BigDecimal subtotal;
//
//    @ManyToOne
//    @JoinColumn(name = "venda_id")
//    @JsonIgnore // Evita loop infinito no retorno do JSON
//    private Pedido venda;
//
//    @ManyToOne
//    @JoinColumn(name = "produto_id")
//    private Produto produto;
//}