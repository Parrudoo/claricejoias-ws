package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadRequestDTO;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
@Tag(name = "Leads", description = "Endpoints para captura e listagem de contatos (Leads)")
public class LeadController {

    @Autowired
    private LeadService service;

    @Operation(summary = "Capturar novo lead", description = "Recebe o nome, WhatsApp e os itens do carrinho, salvando o lead no banco de dados.")
    @PostMapping
    public ResponseEntity<Lead> capturarLead(@RequestBody LeadRequestDTO dto) {
        Lead lead = new Lead();
        lead.setNome(dto.getNome());
        lead.setWhatsapp(dto.getWhatsapp());

        // Transforma o array do carrinho em um texto simples para salvar o histórico
        if (dto.getCarrinho() != null && !dto.getCarrinho().isEmpty()) {
            StringBuilder resumo = new StringBuilder();
            dto.getCarrinho().forEach(item -> {
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