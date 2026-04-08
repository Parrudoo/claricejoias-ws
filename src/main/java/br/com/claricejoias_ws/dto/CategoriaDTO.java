package br.com.claricejoias_ws.dto;

import lombok.Data;

import java.util.List;

@Data
public class CategoriaDTO {

    private Long id;
    private String nome;

    private List<SubcategoriaDTO> subcategorias;
}
