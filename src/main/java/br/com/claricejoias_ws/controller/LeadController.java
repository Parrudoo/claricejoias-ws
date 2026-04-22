package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadRequestDTO;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.model.LeadItem;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.service.KeycloakUserService;
import br.com.claricejoias_ws.service.LeadService;
import br.com.claricejoias_ws.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/leads")
@Tag(name = "Leads", description = "Endpoints para captura e listagem de contatos (Leads)")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService service;
    private final KeycloakUserService keycloakUserService;
    private final ProdutoService produtoService;

    @Operation(summary = "Capturar novo lead", description = "Recebe os dados, cria a conta no Keycloak (se solicitado) e salva o pedido no banco.")
    @PostMapping
    public ResponseEntity<?> capturarLead(@RequestBody LeadRequestDTO dto) {

        // ==========================================
        // ETAPA 1: INTEGRAÇÃO COM KEYCLOAK
        // ==========================================
        if (dto.isCriarConta() && dto.getSenha() != null && !dto.getSenha().trim().isEmpty()) {
            try {
                // Tenta criar o usuário usando o e-mail como login
                keycloakUserService.criarUsuarioCliente(dto.getEmail(), dto.getSenha(), dto.getNome());
            } catch (RuntimeException e) {
                // Se falhar (ex: e-mail já existe), devolve um erro 400 para o React
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            }
        }

        // ==========================================
        // ETAPA 2: SALVAR O PEDIDO COM RELACIONAMENTO
        // ==========================================
        Lead lead = new Lead();
        lead.setNome(dto.getNome());
        lead.setWhatsapp(dto.getWhatsapp());
        lead.setEmail(dto.getEmail());
        lead.setAtivo(true);
        lead.setComprou(false);

        if (dto.getItens() != null && !dto.getItens().isEmpty()) {
            dto.getItens().forEach(itemDto -> {
                // Busca o produto real no banco para fazer a ligação
                Optional<Produto> produtoOpt = produtoService.buscarPorId(itemDto.getProdutoId());

                if (produtoOpt.isPresent()) {
                    LeadItem leadItem = new LeadItem();
                    leadItem.setProduto(produtoOpt.get());
                    leadItem.setQuantidade(itemDto.getQuantidade());
                    leadItem.setPrecoMomento(itemDto.getPreco());
                    lead.addItem(leadItem); // Vincula ao lead
                }
            });
        }

        Lead salvo = service.salvar(lead);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @Operation(summary = "Listar todos os leads", description = "Retorna a lista completa de clientes que enviaram pedidos.")
    @GetMapping
    public ResponseEntity<List<Lead>> listarLeads() {
        return ResponseEntity.ok(service.listarTodos());
    }

    // ==========================================
    // ETAPA 3: ENDPOINTS PARA O PAINEL REACT
    // ==========================================

    @Operation(summary = "Alternar status do lead", description = "Ativa ou desativa um lead existente pelo seu ID.")
    @PutMapping("/{id}/status")
    public ResponseEntity<Lead> alternarStatus(@PathVariable Long id) {
        try {
            Lead leadAtualizado = service.alternarStatus(id);
            return ResponseEntity.ok(leadAtualizado);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Marcar lead como comprado", description = "Altera o status de compra do lead para verdadeiro.")
    @PutMapping("/{id}/compra")
    public ResponseEntity<Lead> marcarComoComprado(@PathVariable Long id) {
        try {
            Lead leadAtualizado = service.marcarComoComprado(id);
            return ResponseEntity.ok(leadAtualizado);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}