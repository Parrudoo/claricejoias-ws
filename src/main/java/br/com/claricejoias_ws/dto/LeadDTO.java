package br.com.claricejoias_ws.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LeadDTO {

    private Long id;
    @NotBlank(message = "O nome não pode estar em branco")
    private String nome;

    @NotBlank(message = "O WhatsApp é obrigatório")
    @Pattern(regexp = "^\\(\\d{2}\\)\\s\\d{5}-\\d{4}$", message = "Formato de WhatsApp inválido. Use (00) 00000-0000")
    private String whatsapp;
    private String email;
    private Boolean ativo = true;
    private Boolean comprou = false;
    private String nomeRevendedor;
    private List<HistoricoDisparoDTO> historicoDisparos;
    private List<LeadItemDTO> itens = new ArrayList<>();
    private ClienteDTO cliente;
}
