package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
@Tag(name = "Produtos", description = "Endpoints para gerenciamento do catálogo de joias")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    @GetMapping
    public ResponseEntity<List<ProdutoDTO>> listarTodos() {
        return ResponseEntity.ok(produtoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Produto> buscarPorId(@PathVariable Long id) {
        return produtoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Cadastrar novo produto com Múltiplas Imagens")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Produto> criar(
            @RequestPart("produto") Produto produto,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) { // Ajustado para List<MultipartFile> e "files"
        try {
            // O ProdutoService agora precisa estar preparado para receber a Lista
            Produto novoProduto = produtoService.salvar(produto, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(novoProduto);
        } catch (Exception e) {
            e.printStackTrace(); // É bom deixar um printStackTrace aqui para ver possíveis erros no console
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Atualizar produto com Múltiplas Imagens")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Produto> atualizar(
            @PathVariable Long id,
            @RequestPart("produto") Produto produto,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) { // Ajustado para List<MultipartFile> e "files"
        try {
            // O ProdutoService agora precisa estar preparado para receber a Lista
            Produto produtoAtualizado = produtoService.atualizar(id, produto, files);
            return ResponseEntity.ok(produtoAtualizado);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        try {
            produtoService.deletar(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}