package br.com.claricejoias_ws.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.ws.rs.GET;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class WhatsappInstance {

    @Id
    private String usuarioId;
    private String instanceName;
    private String uniqueToken;


}
