package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Data
@Entity
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @OneToMany(mappedBy = "cliente")
    private List<Venda> vendas;

    @Column(unique = true) // Evita duplicar o mesmo WhatsApp
    private String telefone;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL)
    private List<HistoricoCobranca> historicoCobrancas;
}