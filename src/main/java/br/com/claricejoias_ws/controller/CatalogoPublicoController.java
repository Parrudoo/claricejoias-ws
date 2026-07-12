package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.ProdutoCatalogoDTO;
import br.com.claricejoias_ws.dto.RevendedorPerfilPublicoDTO;
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

    // 1. Endpoint para carregar a "Cara" da loja da revendedora (Nome/identificação da vitrine)
    // Retorna um DTO enxuto de propósito: este endpoint é público (sem autenticação),
    // então nunca deve expor a WhatsappInstance (contém o uniqueToken da Evolution API)
    // nem outros dados internos do Revendedor.
    @GetMapping("/{slug}/perfil")
    public ResponseEntity<RevendedorPerfilPublicoDTO> getPerfilRevendedor(@PathVariable String slug) {
        Revendedor revendedor = revendedorRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Revendedor não encontrado."));
        return ResponseEntity.ok(new RevendedorPerfilPublicoDTO(revendedor.getId(), revendedor.getNome(), revendedor.getSlug(), revendedor.getWhatsappContato()));
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
