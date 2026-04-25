package br.com.claricejoias_ws.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClienteResponseDTO {
    private Long id;
    private String nome;
    private String telefone;
    private Double valorDevido;
    private UltimaCobrancaDTO ultimaCobranca;

    @Data
    public static class UltimaCobrancaDTO {
        private LocalDateTime dataHora;
        private String funcionario;
    }
}