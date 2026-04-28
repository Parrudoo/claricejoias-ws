package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.dto.LeadRequestDTO;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@RestController
@RequestMapping("/api/leads")
@Tag(name = "Leads e Marketing", description = "Endpoints para captura (Guia de Medidas, Checkout) e gestão do painel de Leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final ModelMapper modelMapper;

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

    @PostMapping("/captura-guia")
    @Operation(summary = "Capturar Lead via Guia", description = "Salva nome e WhatsApp do visitante atrelando ao ID de navegação.")
    public ResponseEntity<Void> registrarLeadGuia(
            @Parameter(description = "ID único do visitante gerado pelo frontend (UUID)")
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @RequestBody LeadDTO dto) {

        leadService.converterEmLead(dto, visitorId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ==========================================================
    // ETAPA 2: CAPTAÇÃO VIA CADASTRO/CHECKOUT
    // ==========================================================

    @PostMapping
    @Operation(summary = "Capturar novo lead no cadastro", description = "Cria conta no Keycloak, vincula o histórico anônimo e salva no banco.")
    public ResponseEntity<Lead> capturarLead(
            @RequestBody LeadRequestDTO dto,
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId) {

        Lead salvo = leadService.processarNovoLead(dto, visitorId);
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
}