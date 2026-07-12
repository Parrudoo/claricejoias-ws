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


    // ROUND-ROBIN: Pega a mensagem mais antiga de cada revendedor (Até 50 por lote).
    // FOR UPDATE SKIP LOCKED evita que duas instâncias da aplicação peguem a mesma linha
    // PENDENTE ao mesmo tempo (precisa rodar dentro de uma transação para o lock valer
    // até a atualização de status — ver @Transactional no chamador).
    @Query(value = """
        WITH Ranked AS (
            SELECT id, ROW_NUMBER() OVER(PARTITION BY revendedor_id ORDER BY data_criacao ASC) as rn
            FROM fila_disparo WHERE status = 'PENDENTE'
        )
        SELECT f.* FROM fila_disparo f
        JOIN Ranked r ON r.id = f.id
        WHERE r.rn = 1
        ORDER BY f.data_criacao ASC
        LIMIT 50
        FOR UPDATE OF f SKIP LOCKED
    """, nativeQuery = true)
    List<FilaDisparo> findNextMessagesFairly();

    Optional<FilaDisparo> findFirstByStatusOrderByDataCriacaoAsc(StatusDisparo status);

    // NOVO: Retorna true se já houver um registro com este status para este lead
    boolean existsByLeadIdAndStatus(Long leadId, StatusDisparo status);

    Optional<FilaDisparo> findFirstByTipoAndStatusOrderByDataCriacaoAsc(String otp, StatusDisparo statusDisparo);
}