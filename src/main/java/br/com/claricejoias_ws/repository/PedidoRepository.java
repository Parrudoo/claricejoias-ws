package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.model.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Busca os pedidos do usuário logado (Mais recentes primeiro)
    List<Pedido> findByUsuarioIdOrderByIdDesc(String usuarioId);

    // Busca os pedidos do visitante anônimo (Mais recentes primeiro)
    List<Pedido> findByVisitorIdOrderByIdDesc(String visitorId);

    @Query("SELECT p FROM Pedido p WHERE (p.visitorId = :visitorId OR p.cliente.usuarioId = :usuarioId) AND p.status = 'CARRINHO'")
    Optional<Pedido> buscarCarrinhoAtivo(@Param("visitorId") String visitorId, @Param("usuarioId") String usuarioId);

    @Query("SELECT p FROM Pedido p WHERE " +
            "(CAST(:revendedorId AS text) IS NULL OR p.revendedor.id = :revendedorId) AND " +
            "(CAST(:loginOperador AS text) IS NULL OR p.loginOperador = :loginOperador) AND " +
            "(CAST(:metodoPagamento AS text) IS NULL OR p.metodoPagamento = :metodoPagamento) AND " +
            "(CAST(:inicioDia AS timestamp) IS NULL OR p.dataCriacao >= :inicioDia) AND " +
            "(CAST(:fimDia AS timestamp) IS NULL OR p.dataCriacao <= :fimDia)")
    Page<Pedido> findComFiltros(
            @Param("revendedorId") String revendedorId,
            @Param("loginOperador") String loginOperador,
            @Param("metodoPagamento") String metodoPagamento,
            @Param("inicioDia") LocalDateTime inicioDia,
            @Param("fimDia") LocalDateTime fimDia,
            Pageable pageable
    );

    Optional<Pedido> findFirstByUsuarioIdAndStatusOrderByIdDesc(String usuarioId, StatusPedido statusPedido);

    Optional<Pedido> findFirstByVisitorIdAndStatusOrderByIdDesc(String visitorId, StatusPedido statusPedido);

    // Busca os pedidos de um usuário específico de forma paginada
    Page<Pedido> findByUsuarioId(String usuarioId, Pageable pageable);
}