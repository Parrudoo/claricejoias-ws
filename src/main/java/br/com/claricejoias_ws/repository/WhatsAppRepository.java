package br.com.claricejoias_ws.repository;

import br.com.claricejoias_ws.model.WhatsappInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WhatsAppRepository extends JpaRepository<WhatsappInstance, Long> {
//    WhatsappInstance findById(String revendedorId);

    WhatsappInstance findByRevendedorIsNull();

}
