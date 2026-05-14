package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.model.Revendedor;
import br.com.claricejoias_ws.repository.RevendedorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/revendedores")
@Tag(name = "Revendedores", description = "Gerenciamento de vínculos com usuários do Keycloak")
public class RevendedorController {

    @Autowired
    private RevendedorRepository repository;

    @Operation(summary = "Vincular novo revendedor", description = "Cria um registro local para um usuário já cadastrado no Keycloak utilizando seu UUID.")
    @PostMapping
    public ResponseEntity<Revendedor> vincularRevendedor(@RequestBody Revendedor revendedor) {
        // O ID aqui deve ser o UUID (Subject) que você copiou do painel do Keycloak
        Revendedor novo = repository.save(revendedor);
        return ResponseEntity.status(HttpStatus.CREATED).body(novo);
    }

    @Operation(summary = "Listar todos os revendedores", description = "Retorna a lista de revendedores vinculados ao sistema.")
    @GetMapping
    public ResponseEntity<List<Revendedor>> listarTodos() {
        return ResponseEntity.ok(repository.findAll());
    }

    @Operation(summary = "Buscar revendedor por ID", description = "Busca os detalhes de um revendedor pelo seu ID do Keycloak.")
    @GetMapping("/{id}")
    public ResponseEntity<Revendedor> buscarPorId(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Remover vínculo de revendedor", description = "Remove o revendedor do sistema local (não exclui o usuário do Keycloak).")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}