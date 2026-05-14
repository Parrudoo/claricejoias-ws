package br.com.claricejoias_ws.model;

import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.model.Cliente;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class FilaCobranca {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Cliente cliente;

    @Column(columnDefinition = "TEXT")
    private String texto;

    private String operador;

    @Enumerated(EnumType.STRING)
    private StatusDisparo status; // PENDENTE, ENVIADO, ERRO

    private LocalDateTime dataCriacao;
    private String mensagemErro;
    @Column(name = "instancia_whatsapp")
    private String instanciaWhatsapp;

}
