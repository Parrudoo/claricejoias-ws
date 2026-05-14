package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.ProdutoCatalogoDTO;
import br.com.claricejoias_ws.model.Revendedor;
import br.com.claricejoias_ws.repository.RevendedorRepository;
import br.com.claricejoias_ws.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogo-publico")
@RequiredArgsConstructor
public class CatalogoPublicoController {

    private final ProdutoService produtoService;
    private final RevendedorRepository revendedorRepository;

    // 1. Endpoint para carregar a "Cara" da loja da revendedora (Foto, Nome, WhatsApp)
    @GetMapping("/{slug}/perfil")
    public ResponseEntity<Revendedor> getPerfilRevendedor(@PathVariable String slug) {
        Revendedor revendedor = revendedorRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Revendedor não encontrado."));
        return ResponseEntity.ok(revendedor);
    }

    // 2. Endpoint para carregar os produtos DAQUELA revendedora
    @GetMapping("/{slug}/produtos")
    public ResponseEntity<Page<ProdutoCatalogoDTO>> getProdutosCatalogo(
            @PathVariable String slug,
            Pageable pageable) {

        // Aqui você chama o repository que criamos no passo 2
        return ResponseEntity.ok(produtoService.listarCatalogoRevendedor(slug, pageable));
    }
}
