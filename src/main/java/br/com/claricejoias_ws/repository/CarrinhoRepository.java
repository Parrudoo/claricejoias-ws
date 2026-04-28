package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Carrinho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {
    // Busca o carrinho usando o UUID do visitante
    Optional<Carrinho> findFirstByVisitorId(String visitorId);

    Optional<Carrinho> findFirstByUsuarioId(String usuarioId);
}