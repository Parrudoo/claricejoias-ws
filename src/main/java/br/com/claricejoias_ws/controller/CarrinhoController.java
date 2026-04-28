package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.CarrinhoDTO;
import br.com.claricejoias_ws.model.Carrinho;
import br.com.claricejoias_ws.service.CarrinhoService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carrinho")
public class CarrinhoController {

    @Autowired private CarrinhoService carrinhoService;
    @Autowired private ModelMapper modelMapper;

    private CarrinhoDTO converterParaDTO(Carrinho carrinho) {
        return modelMapper.map(carrinho, CarrinhoDTO.class);
    }

//    @GetMapping
//    public ResponseEntity<CarrinhoDTO> obterCarrinho(
//            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
//            @AuthenticationPrincipal Jwt jwt) { // Puxa o token se o utilizador enviou
//
//        String usuarioId = (jwt != null) ? jwt.getSubject() : null; // Pega o ID do Keycloak
//
//        Carrinho carrinho = carrinhoService.obterOuCriarCarrinho(visitorId, usuarioId);
//        return ResponseEntity.ok(converterParaDTO(carrinho));
//    }


    @GetMapping
    public ResponseEntity<CarrinhoDTO> buscarMeuCarrinho(
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @RequestHeader(value = "X-Usuario-ID", required = false) String usuarioId) {

        // Usa a consulta inofensiva!
        CarrinhoDTO carrinho = carrinhoService.consultarCarrinhoAtual(visitorId, usuarioId);

        if (carrinho == null) {
            // Se não tem carrinho, devolve 204 No Content (O React entende que a maleta tá vazia)
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(carrinho);
    }


    @PostMapping("/adicionar/{produtoId}")
    public ResponseEntity<CarrinhoDTO> adicionarItem(
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long produtoId,
            @RequestParam(defaultValue = "1") Integer quantidade) {

        String usuarioId = (jwt != null) ? jwt.getSubject() : null;
        Carrinho carrinhoAtualizado = carrinhoService.adicionarItem(visitorId, usuarioId, produtoId, quantidade);
        return ResponseEntity.ok(converterParaDTO(carrinhoAtualizado));
    }

    @DeleteMapping("/remover/{produtoId}")
    public ResponseEntity<CarrinhoDTO> removerItem(
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long produtoId) {

        String usuarioId = (jwt != null) ? jwt.getSubject() : null;
        Carrinho carrinhoAtualizado = carrinhoService.removerItem(visitorId, usuarioId, produtoId);
        return ResponseEntity.ok(converterParaDTO(carrinhoAtualizado));
    }
}