package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.HistoricoCobranca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HistoricoCobrancaRepository extends JpaRepository<HistoricoCobranca, Long> {

    // Busca a última cobrança feita a um cliente específico, ordenada pela data mais recente
    Optional<HistoricoCobranca> findFirstByClienteIdOrderByDataHoraDesc(Long clienteId);
}