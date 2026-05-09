//package br.com.claricejoias_ws.repository;
//
//import br.com.claricejoias_ws.enums.StatusCarrinho;
//import br.com.claricejoias_ws.model.Carrinho;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.stereotype.Repository;
//
//import java.util.Optional;
//
//@Repository
//public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {
//    // Busca o carrinho usando o UUID do visitante
//
//
//
//    // Busca o carrinho de um usuário logado, filtrando pelo status e pegando o mais recente
//    Optional<Carrinho> findFirstByUsuarioIdAndStatusOrderByIdDesc(String usuarioId, StatusCarrinho status);
//
//    // Busca o carrinho de um visitante (anônimo), filtrando pelo status e pegando o mais recente
//    Optional<Carrinho> findFirstByVisitorIdAndStatusOrderByIdDesc(String visitorId, StatusCarrinho status);
//}