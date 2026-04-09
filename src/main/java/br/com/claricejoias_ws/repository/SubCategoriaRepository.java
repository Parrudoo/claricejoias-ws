package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Subcategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubCategoriaRepository extends JpaRepository<Subcategoria,Long> {

    List<Subcategoria> findByCategoriaId(Long categoriaId);
}
