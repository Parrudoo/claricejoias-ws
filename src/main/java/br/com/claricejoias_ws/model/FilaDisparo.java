package br.com.claricejoias_ws.model;

import br.com.claricejoias_ws.enums.StatusDisparo;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class FilaDisparo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "lead_id")
    private Lead lead;
    @Column(columnDefinition = "TEXT")
    private String texto;
    private String operador;
    private String numeroDestino;
    @Enumerated(EnumType.STRING)
    private StatusDisparo status;
    private LocalDateTime dataCriacao;
    @Column(columnDefinition = "TEXT")
    private String mensagemErro;
    private String tipo;
    private LocalDateTime dataDisparo;
    private String motivoFalha;
    private String urlImagem;
    @Column(name = "instancia_whatsapp")
    private String instanciaWhatsapp;
}