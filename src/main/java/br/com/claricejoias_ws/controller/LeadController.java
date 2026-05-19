package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.*;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.service.CarrinhoService;
import br.com.claricejoias_ws.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/leads")
@Tag(name = "Leads e Marketing", description = "Endpoints para captura (Guia de Medidas, Checkout) e gestão do painel de Leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final ModelMapper modelMapper;
    private final CarrinhoService carrinhoService;


    // ==========================================================
    // ETAPA 1: CAPTAÇÃO VIA ISCA DIGITAL (Guia de Medidas/E-book)
    // ==========================================================

    @GetMapping("/status-guia")
    @Operation(summary = "Verificar status do Guia", description = "Verifica se o botão de download do e-book deve aparecer para o visitante.")
    public boolean verificarStatusGuia(
            @Parameter(description = "ID único do visitante gerado pelo frontend (UUID)")
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt) {

        // Se logado ou sem visitante registrado, valida a regra de exibição
        if (jwt != null) return false;
        if (visitorId == null) return true;

        return leadService.deveMostrarBotaoGuia(visitorId);
    }

    @PostMapping("/registrar-lead")
    @Operation(summary = "Capturar Lead via Guia/Pop-up", description = "Salva nome e WhatsApp do visitante atrelando ao ID de navegação e devolve um cupom de desconto.")
    public ResponseEntity<Map<String, String>> registrarLeadGuia(
            @Parameter(description = "ID único do visitante gerado pelo frontend (UUID)")
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @RequestBody LeadDTO dto) {

        // 1. Chama o service e guarda o cupom retornado
        String cupomGerado = leadService.converterEmLead(dto, visitorId);

        // 2. Monta a resposta JSON para o Frontend
        Map<String, String> response = new HashMap<>();
        response.put("cupom", cupomGerado);
        response.put("mensagem", "Desconto liberado com sucesso!");

        // 3. Retorna HTTP 201 (Created) com o JSON no corpo
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ==========================================================
    // ETAPA 2: CAPTAÇÃO VIA CADASTRO/CHECKOUT
    // ==========================================================

    @PostMapping
    @Operation(summary = "Capturar novo lead no cadastro", description = "Cria conta no Keycloak, vincula o histórico anônimo e salva no banco.")
    public ResponseEntity<Lead> capturarLead(
            @RequestBody LeadRequestDTO dto,
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String revendedorId) {

        String usuarioId = (jwt != null) ? jwt.getSubject() : null;

        // Passando o revendedorId para dentro da máquina de processamento
        Lead salvo = leadService.processarNovoLead(dto, visitorId, usuarioId, revendedorId);

        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    // ==========================================================
    // ETAPA 3: GESTÃO DO PAINEL ADMINISTRATIVO (React Dashboard)
    // ==========================================================

    @GetMapping
    @Operation(summary = "Listar todos os leads", description = "Retorna a lista de leads paginada para o Dashboard.")
    public ResponseEntity<Page<LeadDTO>> listarLeads(
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(leadService.listarTodos(pageable));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Alternar status", description = "Ativa ou desativa um lead existente pelo seu ID.")
    public ResponseEntity<LeadDTO> alternarStatus(@PathVariable Long id) {
        Lead leadAtualizado = leadService.alternarStatus(id);
        LeadDTO dto = modelMapper.map(leadAtualizado, LeadDTO.class);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{id}/compra")
    @Operation(summary = "Marcar como comprado", description = "Altera o status de compra do lead para verdadeiro.")
    public ResponseEntity<LeadDTO> marcarComoComprado(@PathVariable Long id) {
        try {
            Lead leadAtualizado = leadService.marcarComoComprado(id);
            LeadDTO dto = modelMapper.map(leadAtualizado, LeadDTO.class);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }


    @PostMapping("/solicitar-codigo")
    public ResponseEntity<?> solicitarCodigo(@RequestParam String whatsapp,
                                             @RequestParam String revendedorId) {

        leadService.solicitarCodigoOtp(whatsapp, revendedorId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/validar-codigo")
    public ResponseEntity<?> validarCodigo(@RequestParam String whatsapp, @RequestParam String codigo) {
        boolean valido = leadService.validarCodigoOtp(whatsapp, codigo);
        if (valido) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().body("Código inválido");
        }
    }


    @PostMapping("/aplicar-cupom")
    @Operation(summary = "Aplicar cupom de desconto", description = "Valida e aplica um cupom ao carrinho atual do visitante ou usuário logado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cupom aplicado e carrinho recalculado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Cupom inválido ou carrinho vazio")
    })
    public ResponseEntity<?> aplicarCupom(
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> payload) { // Recebe um JSON simples: {"codigoCupom": "CLARICE20"}

        String usuarioId = (jwt != null) ? jwt.getSubject() : null;
        String codigoCupom = payload.get("codigoCupom");

        if (codigoCupom == null || codigoCupom.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "O código do cupom não pode estar vazio."));
        }

        try {
            // Chama o Service que acabamos de refatorar com BigDecimal
            CarrinhoDTO carrinhoAtualizado = carrinhoService.aplicarCupom(visitorId, usuarioId, codigoCupom);

            // Retorna HTTP 200 com o carrinho completo, já com os totais recalculados
            return ResponseEntity.ok(carrinhoAtualizado);

        } catch (RegraNegocioException e) {
            // Se o cupom for inválido ou o carrinho não existir, devolvemos erro 400
            // O Map.of cria um JSON rápido para o Front-end ler a mensagem e exibir um alerta
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar Lead", description = "Retorna os dados completos de um lead específico para o modal ou página de detalhes.")
    public ResponseEntity<LeadDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(leadService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar Lead", description = "Permite editar os dados de contato ou adicionar observações ao lead.")
    public ResponseEntity<LeadDTO> atualizarLead(@PathVariable Long id, @RequestBody LeadAtualizacaoDTO dto) {
        return ResponseEntity.ok(leadService.atualizarLead(id, dto));
    }


    @GetMapping("/metricas")
    @Operation(summary = "Métricas de Conversão", description = "Retorna os contadores de leads totais e convertidos para os cards do dashboard.")
    public ResponseEntity<MetricasLeadDTO> obterMetricas() {
        return ResponseEntity.ok(leadService.calcularMetricas());
    }

    @PostMapping("/{id}/mensagens")
    @Operation(summary = "Registrar envio de mensagem", description = "Salva no histórico do lead que uma mensagem (WhatsApp/Email) foi disparada.")
    public ResponseEntity<Void> registrarDisparo(
            @PathVariable Long id,
            @RequestBody MensagemLogDTO dto) {
        leadService.registrarHistoricoMensagem(id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}