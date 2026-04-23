package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.exceptions.RegraNegocioException; // 👇 IMPORTANTE: Importe a sua exceção!
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.LeadRepository;
import br.com.claricejoias_ws.service.AutenticacaoService;
import br.com.claricejoias_ws.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mensagens")
@RequiredArgsConstructor
public class MensagemController {

    private final WhatsAppService whatsAppService;
    private final LeadRepository leadRepository;
    private final AutenticacaoService autenticacaoService;

    @PostMapping("/disparar")
    public ResponseEntity<String> dispararMensagem(@RequestBody MensagemRequestDTO request) {

        // 1. Busca o Lead ou lança a sua exceção global automaticamente se não achar
        Lead lead = leadRepository.findById(request.leadId())
                .orElseThrow(() -> new RegraNegocioException("Lead não encontrado com o ID fornecido."));

        // 2. Chama o serviço (qualquer erro aqui dentro também vai estourar a exceção global)
        whatsAppService.enviarMensagemTexto(lead, request.texto(), autenticacaoService.getUsername());

        // 3. Se chegou até aqui, deu tudo certo!
        return ResponseEntity.ok("Mensagem disparada com sucesso para " + lead.getNome());
    }

    public record MensagemRequestDTO(Long leadId, String texto) {}
}