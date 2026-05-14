package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.EstoqueRevendedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstoqueRevendedorRepository extends JpaRepository<EstoqueRevendedor, Long> {
    Optional<EstoqueRevendedor> findByProdutoIdAndRevendedorId(Long produtoId, String revendedorId);

    List<EstoqueRevendedor> findByRevendedorId(String revendedorId);
}
