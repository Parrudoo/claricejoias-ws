package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.CategoriaService;
import br.com.claricejoias_ws.dto.CategoriaDTO;
import br.com.claricejoias_ws.dto.SubcategoriaDTO;
import br.com.claricejoias_ws.model.Categoria;
import br.com.claricejoias_ws.model.Subcategoria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Categorias", description = "Endpoints para gerenciamento das categorias de joias e acessórios")
public class CategoriaController {

    @Autowired
    private CategoriaService service;

    @Operation(summary = "Listar todas as categorias", description = "Retorna uma lista completa de categorias com suas respectivas subcategorias")
    @GetMapping
    public List<CategoriaDTO> listar() {
        List<CategoriaDTO> categorias = service.listarTodas();
        return categorias;
    }

    @Operation(summary = "Listar subcategorias de uma categoria", description = "Retorna a lista de subcategorias vinculadas a uma categoria específica através do seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de subcategorias retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
    })
    @GetMapping("/{id}/subcategorias")
    public ResponseEntity<List<Subcategoria>> listarSubcategoriasPorCategoria(
            @Parameter(description = "ID da categoria pai") @PathVariable Long id) {
        try {
            // O tipo de retorno na lista dependerá do que você usa no seu Service (Subcategoria ou SubcategoriaDTO)
            return ResponseEntity.ok(service.listarSubcategoriasPorCategoriaId(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Cadastrar nova categoria", description = "Cria uma nova categoria no sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Categoria cadastrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos")
    })
    @PostMapping
    public ResponseEntity<Categoria> cadastrar(@RequestBody Categoria categoria) {
        return ResponseEntity.ok(service.salvar(categoria));
    }

    @Operation(summary = "Atualizar categoria", description = "Atualiza o nome ou dados de uma categoria existente via ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Categoria atualizada com sucesso"),
            @ApiResponse(responseCode = "404", description = "ID da categoria não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<Categoria> atualizar(
            @Parameter(description = "ID da categoria a ser atualizada") @PathVariable Long id,
            @RequestBody Categoria categoria) {
        try {
            return ResponseEntity.ok(service.atualizar(id, categoria));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Deletar categoria", description = "Remove uma categoria permanentemente do sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Categoria removida com sucesso"),
            @ApiResponse(responseCode = "404", description = "ID da categoria não encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @Parameter(description = "ID da categoria a ser removida") @PathVariable Long id) {
        try {
            service.deletar(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}