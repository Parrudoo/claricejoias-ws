package br.com.claricejoias_ws.dto;

import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class CategoriaDTO {

    private Long id;
    private String nome;

    private Set<SubcategoriaDTO> subcategorias;
}
