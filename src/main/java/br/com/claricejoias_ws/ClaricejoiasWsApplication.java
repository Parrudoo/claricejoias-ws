package br.com.claricejoias_ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClaricejoiasWsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClaricejoiasWsApplication.class, args);
	}

}
