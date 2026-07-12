package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.enums.StatusParcela;
import br.com.claricejoias_ws.model.Parcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;


@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, Long> {

    @Modifying
    @Query("UPDATE Parcela p SET p.status = :statusAtrasada WHERE p.status NOT IN (:statusPaga, :statusCancelada) AND p.dataVencimento <= :hoje")
    int marcarParcelasVencidas(
            @Param("statusAtrasada") StatusParcela statusAtrasada,
            @Param("statusPaga") StatusParcela statusPaga,
            @Param("statusCancelada") StatusParcela statusCancelada,
            @Param("hoje") LocalDate hoje
    );
}
