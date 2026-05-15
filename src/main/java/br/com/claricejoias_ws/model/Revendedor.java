package br.com.claricejoias_ws.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
public class Revendedor {

    @Id
    private String id; // Este será o ID (Subject) gerado pelo Keycloak

    private String nome;
    private String email;
    private Boolean ativo = true;
    private String slug;



    @Column(name = "instancia_whatsapp")
    private String instanciaWhatsapp;

    // NOVO CAMPO: O percentual de lucro deste revendedor (Ex: 35.00)
    @Column(name = "percentual_comissao", precision = 5, scale = 2)
    private BigDecimal percentualComissao = new BigDecimal("30.00");

}