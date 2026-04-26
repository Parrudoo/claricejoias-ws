package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class HistoricoCobranca {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dataHora;
    private String funcionario;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
}