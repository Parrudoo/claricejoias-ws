package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.model.Revendedor;
import br.com.claricejoias_ws.service.*;
// Supondo que você tenha um RevendedorService para buscar a instância dele
// import br.com.claricejoias_ws.service.RevendedorService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mensagens")
@RequiredArgsConstructor
public class MensagemController {

    private final WhatsAppService whatsAppService;
    private final LeadService leadService;
    private final AutenticacaoService autenticacaoService;
    private final MinioService minioService;
    private final ModelMapper modelMapper;
    private final RevendedorService revendedorService;

    @PostMapping("/disparar")
    public ResponseEntity<String> dispararMensagem(@RequestBody MensagemRequestDTO request,
    @AuthenticationPrincipal Jwt jwt) {
        String usuarioId = (jwt != null) ? jwt.getSubject() : null;
        LeadDTO lead = leadService.buscarPorId(request.leadId());
        String operador = autenticacaoService.getUsername();

         Revendedor revendedor = revendedorService.findById(usuarioId)
                 .orElseThrow(()-> new RegraNegocioException("Instancia não encontrada para esse Usuario!"));



        // 1. Monta o texto
        StringBuilder textoIntro = new StringBuilder();
        textoIntro.append("Olá, ").append(lead.getNome()).append(". ");
        textoIntro.append("Aqui é da equipe de atendimento da Clarice Joias.\n\n");

        if (lead.getItens() != null && !lead.getItens().isEmpty()) {
            textoIntro.append("Notamos o seu excelente gosto por nossas peças e vimos que alguns itens estão aguardando em seu carrinho!\n");
        } else {
            textoIntro.append("Vimos que você demonstrou interesse em nossas coleções.\n");
        }
        textoIntro.append("Temos uma condição exclusiva liberada para você finalizar seu pedido hoje. Gostaria de conferir as opções?");

        // 2. Dispara a mensagem (Passando a instância)
        whatsAppService.enviarMensagemTexto(lead, textoIntro.toString(), operador, revendedor);

        if (lead.getItens() != null && !lead.getItens().isEmpty()) {
            for (var item : lead.getItens()) {
                var produto = item.getProduto();

                if (produto.getImagens() != null && !produto.getImagens().isEmpty()) {
                    try {
                        String objectName = produto.getImagens().get(0);
                        String path = produto.getImagens().get(0);
                        String legendaDaFoto = "💍 *" + produto.getNome() + "*";

                        // Chama o envio de mídia (Passando a instância)
                        whatsAppService.enviarMensagemImagem(modelMapper.map(lead, Lead.class), legendaDaFoto, path, operador, revendedor);

                    } catch (Exception e) {
                        System.out.println("Erro ao agendar imagem: " + e.getMessage());
                    }
                } else {
                    String textoSemFoto = "💍 *" + produto.getNome() + "* (Imagem indisponível)";
                    // Passando a instância
                    whatsAppService.enviarMensagemTexto(lead, textoSemFoto, operador, revendedor);
                }
            }
        }

        return ResponseEntity.ok("Mensagem enfileirada com sucesso para " + lead.getNome());
    }

    public record MensagemRequestDTO(Long leadId) {}
}