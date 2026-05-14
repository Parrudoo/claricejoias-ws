package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto,Long> {
    List<Produto> findBySubcategoriaId(Long id);

    Optional<Produto> findByCodigo(String codigo);

    List<Produto> findBySubcategoriaIsNull();

    List<Produto> findByRascunhoTrue();

    @Query(value = "SELECT p FROM Produto p " +
            "JOIN FETCH p.subcategoria s " +
            "JOIN FETCH s.categoria c, " +
            "EstoqueRevendedor er " +
            "WHERE er.produto = p " +
            "AND er.revendedor.slug = :slug " +
            "AND er.quantidade > 0 " +
            "AND p.ativo = true " +
            "AND p.rascunho = false",
            countQuery = "SELECT COUNT(p) FROM Produto p, EstoqueRevendedor er " +
                    "WHERE er.produto = p " +
                    "AND er.revendedor.slug = :slug " +
                    "AND er.quantidade > 0 " +
                    "AND p.ativo = true " +
                    "AND p.rascunho = false")
    Page<Produto> findCatalogoPorRevendedorSlug(@Param("slug") String slug, Pageable pageable);

    Optional<Produto> findByCodigoIgnoreCase(String codigoProduto);
}
