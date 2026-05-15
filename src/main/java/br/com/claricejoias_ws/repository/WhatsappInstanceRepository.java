package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.WhatsappInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsappInstanceRepository extends JpaRepository<WhatsappInstance,Long> {
    Optional<WhatsappInstance> findByUsuarioId(String usuarioId);
}
