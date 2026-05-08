package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// 👇 Substituímos o @Data por Getter e Setter para evitar Loop Infinito de memória
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

    // Relacionamento com os itens de interesse
    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeadItem> itens = new ArrayList<>();

    // Relacionamento com o histórico ordenado
    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dataHoraDisparo ASC")
    private List<HistoricoDisparo> historicoDisparos = new ArrayList<>();

    // Método utilitário para garantir o vínculo bidirecional
    public void addItem(LeadItem item) {
        itens.add(item);
        item.setLead(this);
    }

    public void setWhatsapp(String whatsapp) {
        // Se vier nulo, guarda nulo. Se vier preenchido, limpa tudo que não for número.
        this.whatsapp = (whatsapp != null) ? whatsapp.replaceAll("[^0-9]", "") : null;
    }


}