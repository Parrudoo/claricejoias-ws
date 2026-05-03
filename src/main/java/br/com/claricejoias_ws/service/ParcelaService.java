package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.enums.StatusParcela;
import br.com.claricejoias_ws.repository.ParcelaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class ParcelaService {

    private final ParcelaRepository parcelaRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void verificarParcelasVencidas() {
        System.out.println("Iniciando verificação automática de parcelas vencidas...");

        LocalDate hoje = LocalDate.now();

        int qtdAtualizadas = parcelaRepository.marcarParcelasVencidas(
                StatusParcela.ATRASADA,
                StatusParcela.PAGA,
                hoje
        );

        System.out.println(qtdAtualizadas + " parcelas foram marcadas como ATRASADAS.");
    }
}
