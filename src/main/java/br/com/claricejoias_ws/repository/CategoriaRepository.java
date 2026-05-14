package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria,Long> {

    // Adicione no seu CategoriaRepository.java
    @Query("SELECT DISTINCT c FROM Categoria c " +
            "JOIN FETCH c.subcategorias s " +
            "JOIN FETCH s.itens p " +
            "INNER JOIN EstoqueRevendedor er ON p.id = er.produto.id " +
            "WHERE er.revendedor.slug = :slug " +
            "AND er.quantidade > 0 " +
            "AND p.ativo = true " +
            "AND p.rascunho = false")
    List<Categoria> findVitrineDoRevendedor(@Param("slug") String slug);
}
