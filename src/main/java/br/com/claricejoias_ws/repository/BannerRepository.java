package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    // O React vai chamar isso aqui para exibir na Home!
    List<Banner> findByAtivoTrueOrderByOrdemAsc();
}
