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

    // O elo de ligação com a navegação anônima (Cookies do Front)
    @Column(name = "visitor_id", unique = true)
    private String visitorId;

    // O CAMPO NOVO: Elo de ligação com o Cliente Oficial (Keycloak)
    @Column(name = "usuario_id")
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
}