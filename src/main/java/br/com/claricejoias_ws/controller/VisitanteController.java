package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.service.VisitanteService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/visitantes")
@RequiredArgsConstructor
public class VisitanteController {

    private final VisitanteService visitanteService;

    @GetMapping("/status-guia")
    public boolean verificarStatusGuia(HttpServletRequest request, HttpServletResponse response) {
        String uuid = recuperarUuid(request);

        // Se for nulo, o service cria um novo e o controller gera o cookie
        if (uuid == null) {
            String novoUuid = visitanteService.inicializarOuRecuperarVisitante(null);
            adicionarCookie(response, novoUuid);
            return true;
        }

        return visitanteService.deveMostrarBotaoGuia(uuid);
    }

    @PostMapping("/registrar-lead")
    public void registrarLead(@RequestBody LeadDTO dto, HttpServletRequest request) {
        String uuid = recuperarUuid(request);
        visitanteService.converterEmLead(dto, uuid);
    }

    // Métodos utilitários privados para não poluir a lógica principal
    private String recuperarUuid(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "visitor_id".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void adicionarCookie(HttpServletResponse response, String uuid) {
        Cookie cookie = new Cookie("visitor_id", uuid);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(60 * 60 * 24 * 365);
        cookie.setPath("/");
        response.addCookie(cookie);
    }
}