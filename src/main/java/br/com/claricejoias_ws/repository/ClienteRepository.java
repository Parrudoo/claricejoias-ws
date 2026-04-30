package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {


    // Busca apenas os clientes que têm alguma venda com valor devido maior que zero
    @Query("SELECT DISTINCT c FROM Cliente c " +
            "JOIN c.vendas v " +
            "JOIN v.parcelasDetalhadas p " +
            "WHERE p.status = 'PENDENTE' AND p.dataVencimento < CURRENT_DATE")
    List<Cliente> findClientesInadimplentes();

    Optional<Cliente> findByWhatsapp(String whatsapp);

    boolean existsByWhatsapp(String whatsapp);

    Optional<Cliente> findByUsuarioId(String usuarioId);
}