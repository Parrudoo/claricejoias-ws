package br.com.claricejoias_ws.dto;

import br.com.claricejoias_ws.model.Lead;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HistoricoDisparoDTO {
    private Long id;
    private LocalDateTime dataHoraDisparo;
    private String operador;


}
