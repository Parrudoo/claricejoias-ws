package br.com.claricejoias_ws.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LeadDTO {

    private Long id;
    private String nome;
    private String whatsapp;
    private String email;
    private Boolean ativo = true;
    private Boolean comprou = false;
    private List<LeadItemDTO> itens = new ArrayList<>();
}
