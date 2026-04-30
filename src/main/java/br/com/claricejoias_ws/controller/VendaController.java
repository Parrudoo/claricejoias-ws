package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.CheckoutDTO;
import br.com.claricejoias_ws.dto.VendaDTO;
import br.com.claricejoias_ws.dto.VendaRequestDTO;
import br.com.claricejoias_ws.model.Venda;
import br.com.claricejoias_ws.service.AutenticacaoService;
import br.com.claricejoias_ws.service.VendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
@Tag(name = "Vendas", description = "Endpoints para gerenciamento do PDV e Caixa")
public class VendaController {

    private final VendaService vendaService;
    private final AutenticacaoService autenticacaoService;

    @Operation(summary = "Registrar nova venda (PDV)", description = "Utilizado pelo operador para registrar uma venda manual realizada fisicamente.")
    @PostMapping
    public ResponseEntity<?> registrarVenda(@RequestBody VendaRequestDTO dto) {
        try {
            // O seu Service deve processar os itens, baixar o estoque e salvar o financeiro
            Venda vendaSalva = vendaService.registrarVenda(dto,autenticacaoService.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(vendaSalva);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> finalizarPedido(
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CheckoutDTO checkoutDTO) {

        try {
            // Extrai o ID do Keycloak do Token
            String usuarioId = (jwt != null) ? jwt.getSubject() : null;

            // Chama o serviço passando todas as identidades possíveis
            Venda vendaFinalizada = vendaService.realizarCheckout(visitorId, usuarioId, checkoutDTO);
            return ResponseEntity.ok(vendaFinalizada);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }



    @Operation(summary = "Listar vendas", description = "Retorna o histórico de todas as vendas realizadas.")
    @GetMapping
    public ResponseEntity<Page<VendaDTO>> listarVendas(@PageableDefault(page = 0, size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(vendaService.listarVendas(pageable));
    }
}