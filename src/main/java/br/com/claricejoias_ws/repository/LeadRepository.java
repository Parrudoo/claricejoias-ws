package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Lead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    // Retorna todos os leads ativos que demonstraram interesse em uma Subcategoria específica
    @Query("SELECT DISTINCT l FROM Lead l JOIN l.pedidos p JOIN p.itens i " +
            "WHERE i.produto.subcategoria.id = :subcategoriaId AND l.ativo = true")
    List<Lead> findLeadsParaCampanhaPorSubcategoria(@Param("subcategoriaId") Long subcategoriaId);

    Page<Lead> findByAtivoTrueAndComprouFalse(Pageable pageable);
    Optional<Lead> findByWhatsapp(String whatsapp);
    Optional<Lead> findByVisitorId(String visitorId);
    boolean existsByWhatsapp(String whatsapp);
    boolean existsByVisitorId(String visitorId);
    Optional<Lead> findFirstByVisitorIdOrderByIdDesc(String visitorId);

    Optional<Lead> findFirstByVisitorIdAndRevendedorIsNullOrderByIdDesc(String visitorId);

    Optional<Lead> findFirstByVisitorIdAndRevendedorIdOrderByIdDesc(String visitorId, String revendedorId);

    Optional<Lead> findByWhatsappAndRevendedorIsNull(String whatsapp);

    Optional<Lead> findByWhatsappAndRevendedorId(String whatsapp, String revendedorId);

    long countByComprouTrue();
}