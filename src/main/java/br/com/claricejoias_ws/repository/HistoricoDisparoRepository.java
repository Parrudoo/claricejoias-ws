package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.HistoricoDisparo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HistoricoDisparoRepository extends JpaRepository<HistoricoDisparo, Long> {

    // Busca o último disparo feito para esse Lead, ordenando da data mais recente para a mais antiga
    Optional<HistoricoDisparo> findTopByLeadIdOrderByDataHoraDisparoDesc(Long leadId);
}