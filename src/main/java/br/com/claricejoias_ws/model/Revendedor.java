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

    @Column(unique = true, nullable = false)
    private String slug;

    // Número exibido para o CLIENTE clicar e falar com a revendedora (ex: rodapé da vitrine).
    // Independente da WhatsappInstance, que é a instância da Evolution API usada para o
    // ENVIO AUTOMÁTICO de mensagens (lead, OTP etc.) — os dois podem até ser o mesmo número
    // físico, mas servem propósitos diferentes.
    private String whatsappContato;

    @OneToOne(mappedBy = "revendedor", cascade = CascadeType.ALL, orphanRemoval = true)
    private WhatsappInstance whatsappInstance;

    // CAMPO REMOVIDO: private String instanciaWhatsapp; (Evita redundância)

    // O percentual de lucro deste revendedor (Ex: 35.00)
    @Column(name = "percentual_comissao", precision = 5, scale = 2)
    private BigDecimal percentualComissao = new BigDecimal("30.00");

}