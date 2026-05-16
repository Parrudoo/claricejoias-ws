package br.com.claricejoias_ws.model;

import br.com.claricejoias_ws.enums.TipoInstancia;
import jakarta.persistence.Column;
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
    @Column(unique = true, nullable = false)
    private String instanceName;
    private String uniqueToken;
    private TipoInstancia tipoInstancia;


}
