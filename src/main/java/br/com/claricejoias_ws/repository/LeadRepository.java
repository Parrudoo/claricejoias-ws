package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    // Retorna todos os leads ativos que demonstraram interesse em uma Subcategoria específica
    @Query("SELECT DISTINCT l FROM Lead l JOIN l.itens i WHERE i.produto.subcategoria.id = :subcategoriaId AND l.ativo = true")
    List<Lead> findLeadsParaCampanhaPorSubcategoria(@Param("subcategoriaId") Long subcategoriaId);

    List<Lead> findByAtivoTrueAndComprouFalse();
}