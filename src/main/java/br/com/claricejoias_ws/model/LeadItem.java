package br.com.claricejoias_ws.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class LeadItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer quantidade;
    private Double precoMomento; // Preço na hora que ele demonstrou interesse

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    @JsonIgnore // Evita loop infinito ao retornar JSON
    private Lead lead;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id")
    private Produto produto;
}