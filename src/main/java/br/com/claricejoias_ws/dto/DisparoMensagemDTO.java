package br.com.claricejoias_ws.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DisparoMensagemDTO implements Serializable {
    private Long idRegistroBanco;
    private String tipoFila; // "DISPARO" ou "COBRANCA" (Para sabermos qual tabela atualizar)
    private String tipoMensagem; // "TEXTO" ou "IMAGEM"
    private String numeroDestino;
    private String texto;
    private String urlImagem;
    private String instanciaWhatsapp;
}