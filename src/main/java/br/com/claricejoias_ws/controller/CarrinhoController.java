package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.CarrinhoDTO;
import br.com.claricejoias_ws.model.Pedido;
import br.com.claricejoias_ws.service.CarrinhoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carrinho")
public class CarrinhoController {

    @Autowired
    private CarrinhoService carrinhoService;

    @GetMapping
    public ResponseEntity<CarrinhoDTO> buscarMeuCarrinho(
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt) {

        String usuarioId = (jwt != null) ? jwt.getSubject() : null;

        // Esse método já deixamos retornando o DTO pronto direto do Service
        CarrinhoDTO carrinho = carrinhoService.consultarCarrinhoAtualDTO(visitorId, usuarioId);

        if (carrinho == null) {
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

        // O service agora devolve um Pedido (que atua como carrinho)
        Pedido carrinhoAtualizado = carrinhoService.adicionarItem(visitorId, usuarioId, produtoId, quantidade);

        return ResponseEntity.ok(carrinhoService.convertToDTO(carrinhoAtualizado));
    }

    @DeleteMapping("/remover/{produtoId}")
    public ResponseEntity<CarrinhoDTO> removerItem(
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long produtoId) {

        String usuarioId = (jwt != null) ? jwt.getSubject() : null;

        // O service agora devolve um Pedido
        Pedido carrinhoAtualizado = carrinhoService.removerItem(visitorId, usuarioId, produtoId);

        return ResponseEntity.ok(carrinhoService.convertToDTO(carrinhoAtualizado));
    }
}