package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.service.VisitanteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/visitantes")
@RequiredArgsConstructor
@Tag(name = "Visitantes e Leads", description = "Endpoints para controle de visitantes anônimos e conversão em leads (ex: Captação pelo Guia de Medidas)")
public class VisitanteController {

    private final VisitanteService visitanteService;

    @GetMapping("/status-guia")
    @Operation(
            summary = "Verificar status do Guia de Medidas",
            description = "Verifica se o visitante atual já baixou o Guia de Medidas. Retorna 'true' se o botão de download deve ser exibido."
    )
    public boolean verificarStatusGuia(
            @Parameter(description = "ID único do visitante gerado pelo frontend (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId) {

        // Se o frontend por algum motivo não mandar o ID, assumimos que é um visitante novo
        // e retornamos 'true' para que ele veja o botão de baixar o guia.
        if (visitorId == null) {
            return true;
        }

        // Se mandou o ID, o service verifica se ele já baixou
        return visitanteService.deveMostrarBotaoGuia(visitorId);
    }

    @PostMapping("/registrar-lead")
    @Operation(
            summary = "Registrar novo Lead",
            description = "Salva os dados de contato do visitante (nome, whatsapp, etc) atrelando-os ao seu ID de navegação."
    )
    public void registrarLead(
            @Parameter(description = "ID único do visitante gerado pelo frontend (UUID)")
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            @RequestBody LeadDTO dto) {

        // O service agora pega os dados digitados e atrela ao visitante usando o UUID do cabeçalho
        visitanteService.converterEmLead(dto, visitorId);
    }
}