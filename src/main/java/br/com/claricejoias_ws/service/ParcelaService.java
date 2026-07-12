package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.enums.StatusParcela;
import br.com.claricejoias_ws.repository.ParcelaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelaService {

    private final ParcelaRepository parcelaRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void verificarParcelasVencidas() {
        log.info("Iniciando verificação automática de parcelas vencidas...");

        LocalDate hoje = LocalDate.now();

        int qtdAtualizadas = parcelaRepository.marcarParcelasVencidas(
                StatusParcela.ATRASADA,
                StatusParcela.PAGA,
                StatusParcela.CANCELADA,
                hoje
        );

        log.info("{} parcelas foram marcadas como ATRASADAS.", qtdAtualizadas);
    }
}
