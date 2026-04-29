package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.model.Banner;
import br.com.claricejoias_ws.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerRepository bannerRepository;

    // ===================================================
    // ROTA PÚBLICA (Para o Front-end da Loja exibir)
    // ===================================================
    @GetMapping("/ativos")
    public ResponseEntity<List<Banner>> listarBannersAtivos() {
        return ResponseEntity.ok(bannerRepository.findByAtivoTrueOrderByOrdemAsc());
    }

    // ===================================================
    // ROTAS PRIVADAS (Para o seu Painel Admin)
    // ===================================================
    @PostMapping
    public ResponseEntity<Banner> criarBanner(@RequestBody Banner banner) {
        // Aqui o Admin manda o objectName gerado pelo MinIO junto com o link e título
        Banner salvo = bannerRepository.save(banner);
        return ResponseEntity.ok(salvo);
    }

    @GetMapping
    public ResponseEntity<List<Banner>> listarTodosOsBanners() {
        return ResponseEntity.ok(bannerRepository.findAll());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> alternarStatus(@PathVariable Long id) {
        Banner banner = bannerRepository.findById(id).orElseThrow();
        banner.setAtivo(!banner.isAtivo());
        bannerRepository.save(banner);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarBanner(@PathVariable Long id) {
        // Opcional: Você pode injetar o MinioService aqui e deletar
        // o arquivo do bucket também usando banner.getObjectName() antes de deletar do banco.
        bannerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}