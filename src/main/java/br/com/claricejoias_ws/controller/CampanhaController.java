package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.service.AgendadorCampanhaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campanha")
public class CampanhaController {

    @Autowired
    private AgendadorCampanhaService agendadorService;

    // Endpoint exclusivo para testes!
    @GetMapping("/disparar-agora")
    public String dispararManualmente() {
        agendadorService.iniciarCampanhaDiaria();
        return "Disparo da campanha iniciado! Verifique os logs do console.";
    }
}