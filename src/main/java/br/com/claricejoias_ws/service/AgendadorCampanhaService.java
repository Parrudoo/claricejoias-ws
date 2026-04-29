package br.com.claricejoias_ws.service;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgendadorCampanhaService {


    private final JobLauncher jobLauncher;
    private final Job campanhaJob;

    // Corre todos os dias às 8h da manhã
    @Scheduled(cron = "0 0 8 * * ?")
    public void iniciarCampanhaDiaria() {
        try {
            // Os JobParameters garantem que o Job corre como uma instância única todos os dias
            JobParameters params = new JobParametersBuilder()
                    .addLong("tempoInicio", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(campanhaJob, params);
            System.out.println("Job da Campanha de WhatsApp iniciado!");
        } catch (Exception e) {
            System.err.println("Erro ao iniciar o Job: " + e.getMessage());
        }
    }
}