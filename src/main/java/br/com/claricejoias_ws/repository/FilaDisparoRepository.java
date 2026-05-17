package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.model.FilaDisparo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FilaDisparoRepository extends JpaRepository<FilaDisparo, Long> {


    // ROUND-ROBIN: Pega a mensagem mais antiga de cada revendedor (Até 50 por lote)
    @Query(value = """
        WITH Ranked AS (
            SELECT *, ROW_NUMBER() OVER(PARTITION BY revendedor_id ORDER BY data_criacao ASC) as rn
            FROM fila_disparo WHERE status = 'PENDENTE'
        )
        SELECT * FROM Ranked WHERE rn = 1 LIMIT 50
    """, nativeQuery = true)
    List<FilaDisparo> findNextMessagesFairly();

    Optional<FilaDisparo> findFirstByStatusOrderByDataCriacaoAsc(StatusDisparo status);

    // NOVO: Retorna true se já houver um registro com este status para este lead
    boolean existsByLeadIdAndStatus(Long leadId, StatusDisparo status);

    Optional<FilaDisparo> findFirstByTipoAndStatusOrderByDataCriacaoAsc(String otp, StatusDisparo statusDisparo);
}