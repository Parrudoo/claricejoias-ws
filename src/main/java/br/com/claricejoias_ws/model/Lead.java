package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Substituímos o @Data por Getter e Setter para evitar Loop Infinito de memória
@Getter
@Setter
@Entity
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String whatsapp;
    private String email;

    private String visitorId;
    private String usuarioId;

    // Controles do painel
    private Boolean ativo = true;
    private Boolean comprou = false;

    // Para sabermos qual cupom ele ganhou
    private String cupomGerado;
    private LocalDateTime dataCadastro;

    @ManyToOne
    @JoinColumn(name = "revendedor_id")
    private Revendedor revendedor;

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL)
    @OrderBy("dataCriacao DESC")
    private List<Pedido> pedidos = new ArrayList<>();

    // Relacionamento com o histórico ordenado
    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dataHoraDisparo ASC")
    private List<HistoricoDisparo> historicoDisparos = new ArrayList<>();


    public void setWhatsapp(String whatsapp) {
        // Se vier nulo, guarda nulo. Se vier preenchido, limpa tudo que não for número.
        this.whatsapp = (whatsapp != null) ? whatsapp.replaceAll("[^0-9]", "") : null;
    }

    @PrePersist
    public void prePersist() {
        this.dataCadastro = LocalDateTime.now();
    }


}