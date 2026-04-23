package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.dto.LeadRequestDTO;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.service.KeycloakUserService;
import br.com.claricejoias_ws.service.LeadService;
import br.com.claricejoias_ws.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
@Tag(name = "Leads", description = "Endpoints para captura e listagem de contatos (Leads)")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final ModelMapper modelMapper;


    @Operation(summary = "Capturar novo lead", description = "Recebe os dados, cria a conta no Keycloak (se solicitado) e salva o pedido no banco.")
    @PostMapping
    public ResponseEntity<Lead> capturarLead(@RequestBody LeadRequestDTO dto) {

        // O Service faz toda a mágica do Keycloak e Banco de Dados
        Lead salvo = leadService.processarNovoLead(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @Operation(summary = "Listar todos os leads", description = "Retorna a lista de clientes paginada.")
    @GetMapping
    public ResponseEntity<Page<LeadDTO>> listarLeads(
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(leadService.listarTodos(pageable));
    }

    // ==========================================
    // ETAPA 3: ENDPOINTS PARA O PAINEL REACT
    // ==========================================

    @Operation(summary = "Alternar status do lead", description = "Ativa ou desativa um lead existente pelo seu ID.")
    @PutMapping("/{id}/status")
    public ResponseEntity<LeadDTO> alternarStatus(@PathVariable Long id) {

        // 1. O serviço faz o trabalho pesado e devolve a Entidade
        Lead leadAtualizado = leadService.alternarStatus(id);

        // 2. Converte para DTO, igualzinho você faz na listagem!
        LeadDTO dto = modelMapper.map(leadAtualizado, LeadDTO.class);

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Marcar lead como comprado", description = "Altera o status de compra do lead para verdadeiro.")
    @PutMapping("/{id}/compra")
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