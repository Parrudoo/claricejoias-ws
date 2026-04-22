package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadRequestDTO;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.service.KeycloakUserService;
import br.com.claricejoias_ws.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
@Tag(name = "Leads", description = "Endpoints para captura e listagem de contatos (Leads)")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService service;
    private final KeycloakUserService keycloakUserService;

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
        // ETAPA 2: SALVAR O PEDIDO NO SEU BANCO
        // ==========================================
        Lead lead = new Lead();
        lead.setNome(dto.getNome());
        lead.setWhatsapp(dto.getWhatsapp());
        lead.setEmail(dto.getEmail()); // Salvando o e-mail no seu histórico

        // Transforma o array do carrinho em um texto simples para salvar o histórico
        if (dto.getItens() != null && !dto.getItens().isEmpty()) {
            StringBuilder resumo = new StringBuilder();
            dto.getItens().forEach(item -> {
                resumo.append(item.getQuantidade()).append("x ")
                        .append(item.getNome())
                        .append(" (R$ ").append(item.getPreco()).append(")\n");
            });
            lead.setItensInteresse(resumo.toString());
        }

        Lead salvo = service.salvar(lead);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @Operation(summary = "Listar todos os leads", description = "Retorna a lista completa de clientes que enviaram pedidos.")
    @GetMapping
    public ResponseEntity<List<Lead>> listarLeads() {
        return ResponseEntity.ok(service.listarTodos());
    }
}