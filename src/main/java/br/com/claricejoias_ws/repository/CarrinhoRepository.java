package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Carrinho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {
    // Busca o carrinho usando o UUID do visitante
    Optional<Carrinho> findByVisitorId(String visitorId);

    Optional<Carrinho> findByUsuarioId(String usuarioId);
}