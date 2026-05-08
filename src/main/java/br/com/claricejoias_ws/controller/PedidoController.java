package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.PedidoDTO;
import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
@Tag(name = "Pedidos", description = "Endpoints para visualização do histórico e gestão de pedidos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Ajuste conforme seu ambiente
public class PedidoController {

    private final PedidoService pedidoService;

    // ==========================================================
    // ÁREA DO CLIENTE
    // ==========================================================

    @GetMapping("/meus-pedidos")
    @Operation(summary = "Listar meus pedidos", description = "Retorna o histórico de compras do usuário logado ou do visitante anônimo.")
    public ResponseEntity<List<PedidoDTO>> buscarMeusPedidos(
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt) {

        String usuarioId = (jwt != null) ? jwt.getSubject() : null;

        if ((visitorId == null || visitorId.isBlank()) && (usuarioId == null || usuarioId.isBlank())) {
            return ResponseEntity.badRequest().build();
        }

        List<PedidoDTO> pedidos = pedidoService.buscarMeusPedidos(visitorId, usuarioId);

        if (pedidos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(pedidos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pedido por ID", description = "Retorna os detalhes de um pedido específico.")
    public ResponseEntity<PedidoDTO> buscarPorId(@PathVariable Long id) {
        PedidoDTO pedido = pedidoService.buscarPorId(id);
        return ResponseEntity.ok(pedido);
    }

    // ==========================================================
    // ÁREA ADMINISTRATIVA (Painel da Vendedora)
    // ==========================================================

    @GetMapping
    @Operation(summary = "Listar todos os pedidos", description = "Retorna todos os pedidos da loja para o painel administrativo.")
    public ResponseEntity<List<PedidoDTO>> listarTodos() {
        // Futuramente você pode adicionar paginação (Pageable) aqui se a loja crescer muito
        List<PedidoDTO> pedidos = pedidoService.listarTodos();

        if (pedidos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(pedidos);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Atualizar status", description = "Muda o status do pedido (ex: de AGUARDANDO_WHATSAPP para CONCLUIDO).")
    public ResponseEntity<PedidoDTO> atualizarStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateDTO payload) {

        PedidoDTO pedidoAtualizado = pedidoService.atualizarStatus(id, payload.getStatusPedido());
        return ResponseEntity.ok(pedidoAtualizado);
    }

    // DTO interno apenas para receber o status via JSON no body do PUT
    @Data
    public static class StatusUpdateDTO {
        private StatusPedido statusPedido;
    }
}