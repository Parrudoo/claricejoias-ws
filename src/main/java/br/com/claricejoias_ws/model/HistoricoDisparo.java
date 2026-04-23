package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor // 👇 ESSA É A SOLUÇÃO MÁGICA
@AllArgsConstructor
public class HistoricoDisparo implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(nullable = false)
    private LocalDateTime dataHoraDisparo;

    @Column(nullable = false)
    private String operador;


    public HistoricoDisparo(Lead lead, LocalDateTime dataHoraDisparo, String operador) {
        this.lead = lead;
        this.dataHoraDisparo = dataHoraDisparo;
        this.operador = operador;
    }
}