package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Busca os pedidos do usuário logado (Mais recentes primeiro)
    List<Pedido> findByUsuarioIdOrderByIdDesc(String usuarioId);

    // Busca os pedidos do visitante anônimo (Mais recentes primeiro)
    List<Pedido> findByVisitorIdOrderByIdDesc(String visitorId);
}