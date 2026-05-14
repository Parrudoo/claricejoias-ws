package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Revendedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


// Crie também o RevendedorRepository
public interface RevendedorRepository extends JpaRepository<Revendedor, String> {


    Optional<Revendedor> findBySlug(String slug);
}
