package br.com.claricejoias_ws.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LeadDTO {

    private Long id;
    private String nome;
    private String whatsapp;
    private String email;
    private Boolean ativo = true;
    private Boolean comprou = false;
    private List<HistoricoDisparoDTO> historicoDisparos;
    private List<LeadItemDTO> itens = new ArrayList<>();
}
