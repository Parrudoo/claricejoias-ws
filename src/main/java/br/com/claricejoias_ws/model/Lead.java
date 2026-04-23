package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String whatsapp;
    private String email;

    // Novos campos para controle do painel
    private Boolean ativo = true;
    private Boolean comprou = false;

    // Relacionamento com os itens de interesse
    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeadItem> itens = new ArrayList<>();

    // Adicione a relação com o histórico e ordene da mais antiga para a mais recente
    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dataHoraDisparo ASC")
    private List<HistoricoDisparo> historicoDisparos = new ArrayList<>();

    // Método utilitário para garantir o vínculo bidirecional
    public void addItem(LeadItem item) {
        itens.add(item);
        item.setLead(this);
    }
}