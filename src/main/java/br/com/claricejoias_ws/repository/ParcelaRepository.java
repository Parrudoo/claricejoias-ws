package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Parcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, Long> {
}
