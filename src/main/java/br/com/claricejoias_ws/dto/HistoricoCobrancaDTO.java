package br.com.claricejoias_ws.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class HistoricoCobrancaDTO {

    private Long id;
    private LocalDateTime dataHora;
    private String funcionario;
}
