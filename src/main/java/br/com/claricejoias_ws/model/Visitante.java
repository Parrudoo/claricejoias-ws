package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "visitantes", indexes = {@Index(name = "idx_visitor_uuid", columnList = "visitor_uuid")})
public class Visitante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_uuid", unique = true, nullable = false)
    private String visitorUuid; // O "Crachá" (Ex: 550e8400-e29b...)

    private String nome;
    private String whatsapp;

    @Column(name = "baixou_guia")
    private boolean baixouGuia = false;

    @Column(name = "data_primeiro_acesso")
    private LocalDateTime dataPrimeiroAcesso;

    // Dados de Cliente (Preenchido quando ele faz cadastro/login via Keycloak)
    @Column(unique = true)
    private String usuarioId;

    @PrePersist
    public void prePersist() {
        this.dataPrimeiroAcesso = LocalDateTime.now();
    }
}