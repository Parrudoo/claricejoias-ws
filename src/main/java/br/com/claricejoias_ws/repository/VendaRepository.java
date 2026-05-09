//package br.com.claricejoias_ws.repository;
//
//import br.com.claricejoias_ws.model.Venda;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//
//@Repository
//public interface VendaRepository extends JpaRepository<Venda, Long> {
//
//
//    @Query("SELECT v FROM Venda v WHERE " +
//            "(CAST(:loginOperador AS text) IS NULL OR v.loginOperador = :loginOperador) AND " +
//            "(CAST(:metodoPagamento AS text) IS NULL OR v.metodoPagamento = :metodoPagamento) AND " +
//            "(CAST(:inicioDia AS timestamp) IS NULL OR v.dataVenda >= :inicioDia) AND " +
//            "(CAST(:fimDia AS timestamp) IS NULL OR v.dataVenda <= :fimDia)")
//    Page<Venda> findComFiltros(
//            @Param("loginOperador") String loginOperador,
//            @Param("metodoPagamento") String metodoPagamento,
//            @Param("inicioDia") LocalDateTime inicioDia,
//            @Param("fimDia") LocalDateTime fimDia,
//            Pageable pageable
//    );
//}