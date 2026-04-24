package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.model.FilaDisparo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FilaDisparoRepository extends JpaRepository<FilaDisparo, Long> {

    Optional<FilaDisparo> findFirstByStatusOrderByDataCriacaoAsc(StatusDisparo status);

    // 👇 NOVO: Retorna true se já houver um registro com este status para este lead
    boolean existsByLeadIdAndStatus(Long leadId, StatusDisparo status);
}