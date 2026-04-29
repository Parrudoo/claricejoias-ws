package br.com.claricejoias_ws.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "banners")
@Getter
@Setter
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String titulo;
    private String objectName;
    private String linkAcao;
    private Integer ordem;
    private boolean ativo = true;
}