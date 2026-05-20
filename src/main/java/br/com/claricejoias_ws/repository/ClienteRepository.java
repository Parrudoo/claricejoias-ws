package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.enums.StatusParcela;
import br.com.claricejoias_ws.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {


    @Query("SELECT DISTINCT c FROM Cliente c " +
            "JOIN c.pedidos v " +
            "JOIN v.parcelasDetalhadas p " +
            "WHERE p.status = :statusPendente AND p.dataVencimento < CURRENT_DATE")
    Page<Cliente> findClientesInadimplentes(@Param("statusPendente") StatusParcela statusPendente,Pageable pageable);

    Optional<Cliente> findByWhatsapp(String whatsapp);
    boolean existsByWhatsapp(String whatsapp);
    Optional<Cliente> findByWhatsappAndRevendedorId(String whatsapp, String revendedorId);
    Optional<Cliente> findByUsuarioId(String usuarioId);
    // ADICIONE ESTA LINHA PARA A TELA DA REVENDEDORA
    Page<Cliente> findByRevendedorId(String revendedorId,Pageable pageable);
    Optional<Cliente> findByWhatsappAndRevendedorIsNull(String whatsapp);

    boolean existsByWhatsappAndRevendedorIsNull(String whatsappLimpo);
    boolean existsByWhatsappAndRevendedorId(String whatsappLimpo, String revendedorId);

    Optional<Cliente> findFirstByWhatsapp(String whatsappLimpo);

    @Query("SELECT DISTINCT c FROM Cliente c " +
            "JOIN c.pedidos v " +
            "JOIN v.parcelasDetalhadas p " +
            "WHERE p.status = :statusPendente AND p.dataVencimento < CURRENT_DATE")
    Page<Cliente> findClientesInadimplentesPorRevendedor(StatusParcela statusParcela, String userId, Pageable pageable);
}