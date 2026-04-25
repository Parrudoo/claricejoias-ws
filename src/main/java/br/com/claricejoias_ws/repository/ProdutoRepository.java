package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto,Long> {
    List<Produto> findBySubcategoriaId(Long id);

    Optional<Produto> findByCodigo(String codigo);
}
