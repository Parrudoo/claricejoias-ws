package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.CheckoutDTO;
import br.com.claricejoias_ws.dto.PedidoDTO;
import br.com.claricejoias_ws.dto.PedidoRequestDTO;
import br.com.claricejoias_ws.model.Pedido;
import br.com.claricejoias_ws.service.AutenticacaoService;
import br.com.claricejoias_ws.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Endpoints unificados para gerenciamento de Vendas (PDV) e E-commerce")
public class PedidoController {

    private final PedidoService pedidoService;
    private final AutenticacaoService autenticacaoService;

    @Operation(summary = "Registrar nova venda (PDV)", description = "Venda manual por Admin ou Revendedor.")
    @PostMapping("/pdv")
    public ResponseEntity<?> registrarPedidoPDV(@RequestBody PedidoRequestDTO dto, JwtAuthenticationToken token) {
        try {
            // Extrai o UUID (sub) do Keycloak
            String userId = token.getToken().getSubject();

            // Verifica se o usuário tem a role de ADMIN
            // Verifica se a autoridade é "ROLE_ADMIN" ou apenas "ADMIN"
            boolean isAdmin = token.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));

            Pedido pedidoSalvo = pedidoService.registrarPedidoPDV(dto, userId, isAdmin, autenticacaoService.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(pedidoSalvo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @Operation(summary = "Finalizar pedido online (Checkout)", description = "Transforma um carrinho ativo do e-commerce em um pedido finalizado.")
    @PostMapping("/checkout")
    public ResponseEntity<?> finalizarPedido(
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CheckoutDTO checkoutDTO) {

        try {
            String usuarioId = (jwt != null) ? jwt.getSubject() : null;
            Pedido pedidoFinalizado = pedidoService.realizarCheckoutOnline(visitorId, usuarioId, checkoutDTO);
            return ResponseEntity.ok(pedidoFinalizado);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Listar pedidos", description = "Retorna o histórico de pedidos consolidados.")
    @GetMapping
    public ResponseEntity<Page<PedidoDTO>> listarPedidos(
            @RequestParam(required = false) String loginOperador,
            @RequestParam(required = false) String metodoPagamento,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @PageableDefault(page = 0, size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(pedidoService.listarPedidos(loginOperador, metodoPagamento, dataInicio, dataFim, pageable));
    }



    @Operation(summary = "Listar meus pedidos", description = "Retorna o histórico de pedidos do cliente logado de forma paginada.")
    @GetMapping("/meus-pedidos")
    public ResponseEntity<Page<PedidoDTO>> listarMeusPedidos(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        String usuarioId = jwt.getSubject();

        Page<PedidoDTO> meusPedidos = pedidoService.listarMeusPedidos(usuarioId, pageable);
        return ResponseEntity.ok(meusPedidos);
    }
}