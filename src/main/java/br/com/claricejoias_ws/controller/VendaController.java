package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.VendaRequestDTO;
import br.com.claricejoias_ws.model.Venda;
import br.com.claricejoias_ws.service.VendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
@Tag(name = "Vendas", description = "Endpoints para gerenciamento do PDV e Caixa")
public class VendaController {

    private final VendaService vendaService;

    @Operation(summary = "Registrar nova venda", description = "Recebe os dados do carrinho do PDV e finaliza a transação.")
    @PostMapping
    public ResponseEntity<Venda> registrarVenda(@RequestBody VendaRequestDTO dto) {
        try {
            Venda vendaSalva = vendaService.registrarVenda(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(vendaSalva);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Listar vendas", description = "Retorna o histórico de todas as vendas realizadas.")
    @GetMapping
    public ResponseEntity<List<Venda>> listarVendas() {
        return ResponseEntity.ok(vendaService.listarVendas());
    }
}