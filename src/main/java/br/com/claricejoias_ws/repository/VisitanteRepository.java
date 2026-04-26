package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Visitante;
import org.modelmapper.Converters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VisitanteRepository extends JpaRepository<Visitante, Long> {

    // Método customizado para o Spring buscar o visitante pelo cookie dele
    Optional<Visitante> findByVisitorUuid(String visitorUuid);

}
