package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Revendedor {

    @Id
    private String id; // Este será o ID (Subject) gerado pelo Keycloak

    private String nome;
    private String email;
    private Boolean ativo = true;
}