package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.*;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.service.AutenticacaoService;
import br.com.claricejoias_ws.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Endpoints para gestão de clientes e histórico de cobranças")
public class ClienteController {

    private final ClienteService clienteService;
    private final AutenticacaoService autenticacaoService;

    @Operation(summary = "Listar todos os clientes cadastrados")
    @GetMapping
    public ResponseEntity<List<ClienteResponseDTO>> listarTodos() {
        return ResponseEntity.ok(clienteService.listarTodos());
    }

    @Operation(summary = "Listar clientes com saldo devedor pendente")
    @GetMapping("/pendentes")
    public ResponseEntity<List<ClienteResponseDTO>> listarPendentes() {
        return ResponseEntity.ok(clienteService.listarPendentes());
    }


    @GetMapping("/me")
    @Operation(summary = "Obter Meu Perfil", description = "Retorna os dados do cliente logado. Se for o primeiro acesso, cria o cliente no banco de dados e sincroniza com os dados de Lead.")
    public ResponseEntity<Cliente> obterMeuPerfil(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId) {

        // Se for a primeira vez, ele cria. Se não, apenas devolve o cliente existente.
        Cliente cliente = clienteService.sincronizarClienteComKeycloak(jwt, visitorId);

        return ResponseEntity.ok(cliente);
    }

    @Operation(summary = "Registrar um histórico de cobrança realizada por um funcionário")
    @PostMapping("/{id}/cobranca")
    public ResponseEntity<String> registrarCobranca(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        clienteService.registrarCobranca(id, autenticacaoService.getUsername());

        return ResponseEntity.ok().build();

    }

    // ==========================================================
    // NOVO ENDPOINT: Buscar os detalhes de compras do cliente
    // ==========================================================
    @Operation(summary = "Listar histórico detalhado de compras de um cliente específico")
    @GetMapping("/{id}/compras")
    public ResponseEntity<List<MovimentacaoDTO>> listarComprasDoCliente(@PathVariable Long id) {
        try {
            // Esse método precisará ser criado no seu ClienteService ou VendaService
            List<MovimentacaoDTO> compras = clienteService.buscarHistoricoCompras(id);
            return ResponseEntity.ok(compras);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }


    // ==========================================================
    // NOVO ENDPOINT: Lançar pagamento (Dar baixa)
    // ==========================================================
    @Operation(summary = "Registrar baixa de pagamento e atualizar saldo do cliente")
    @PostMapping("/{id}/pagamentos")
    public ResponseEntity<Void> registrarPagamento(
            @PathVariable Long id,
            @RequestBody BaixaPagamentoDTO baixaPagamentoDTO) {
        try {
            // O serviço processa a baixa no saldo e gera o histórico
            clienteService.registrarPagamento(id, baixaPagamentoDTO);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}