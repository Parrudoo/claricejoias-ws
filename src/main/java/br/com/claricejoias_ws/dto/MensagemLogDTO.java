package br.com.claricejoias_ws.dto;

import lombok.Data;

@Data
public class MensagemLogDTO {
    private String mensagem;
    private String tipo; // Ex: "WHATSAPP", "EMAIL", "SMS"
}