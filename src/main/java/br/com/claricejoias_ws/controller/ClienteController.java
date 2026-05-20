package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.*;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.service.AutenticacaoService;
import br.com.claricejoias_ws.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    private final ClienteRepository clienteRepository;

    @Operation(summary = "Listar clientes baseados no perfil do usuário logado (Paginado)")
    @GetMapping
    public ResponseEntity<Page<ClienteResponseDTO>> listarTodos(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        // 1. Pega o ID do usuário (geralmente mapeado no 'sub' do token)
        String userId = jwt.getSubject();

        // 2. Extrai a flag de Admin lendo as roles do token
        boolean isAdmin = verificarSeAdmin(jwt);

        // 3. Passa a responsabilidade e o pageable para o Service
        return ResponseEntity.ok(clienteService.listarTodos(userId, isAdmin, pageable));
    }

    private boolean verificarSeAdmin(Jwt jwt) {
        // Estrutura padrão quando se usa Keycloak
        if (jwt.hasClaim("realm_access")) {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                // Verifique o nome exato da sua role (pode ser "admin", "ROLE_ADMIN", etc)
                return roles.contains("ROLE_ADMIN") || roles.contains("admin") || roles.contains("ADMIN");
            }
        }

        // Caso você mapeie as authorities de outra forma, pode verificar assim:
        // jwt.getClaimAsStringList("roles").contains("ROLE_ADMIN");

        return false;
    }

    @Operation(summary = "Listar clientes com saldo devedor pendente (Paginado)")
    @GetMapping("/pendentes")
    public ResponseEntity<Page<ClienteResponseDTO>> listarPendentes(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        String userId = jwt.getSubject();
        boolean isAdmin = verificarSeAdmin(jwt);

        return ResponseEntity.ok(clienteService.listarPendentes(userId, isAdmin, pageable));
    }

    @Operation(summary = "Listar clientes da revendedora", description = "Retorna apenas os clientes vinculados a um revendedor específico.")
    @GetMapping("/revendedor/{revendedorId}")
    public ResponseEntity<Page<Cliente>> listarPorRevendedor(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        String usuarioId = (jwt != null) ? jwt.getSubject() : null;

        // Passamos o pageable para o repositório
        Page<Cliente> clientes = clienteRepository.findByRevendedorId(usuarioId, pageable);

        return ResponseEntity.ok(clientes);
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
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal Jwt jwt) {
        String usuarioId = (jwt != null) ? jwt.getSubject() : null;
        clienteService.registrarCobranca(id, autenticacaoService.getUsername(), usuarioId);

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