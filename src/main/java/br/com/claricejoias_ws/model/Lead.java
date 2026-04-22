package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String whatsapp;

    // Coluna para salvar o texto com os itens que ele selecionou
    @Column(columnDefinition = "TEXT")
    private String itensInteresse;

    private LocalDateTime dataRegistro;

    private String email;

    // Preenche a data automaticamente antes de salvar no banco
    @PrePersist
    protected void onCreate() {
        this.dataRegistro = LocalDateTime.now();
    }
}