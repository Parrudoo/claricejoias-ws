package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Revendedor;
import org.springframework.data.jpa.repository.JpaRepository;

// Crie também o RevendedorRepository
public interface RevendedorRepository extends JpaRepository<Revendedor, String> {


}
