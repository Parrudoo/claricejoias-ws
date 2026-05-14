package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.service.AutenticacaoService;
import br.com.claricejoias_ws.service.LeadService;
import br.com.claricejoias_ws.service.MinioService;
import br.com.claricejoias_ws.service.WhatsAppService;
// Supondo que você tenha um RevendedorService para buscar a instância dele
// import br.com.claricejoias_ws.service.RevendedorService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
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
    private final LeadService leadService;
    private final AutenticacaoService autenticacaoService;
    private final MinioService minioService;
    private final ModelMapper modelMapper;

    @PostMapping("/disparar")
    public ResponseEntity<String> dispararMensagem(@RequestBody MensagemRequestDTO request) {

        LeadDTO lead = leadService.buscarPorId(request.leadId());
        String operador = autenticacaoService.getUsername();

        // IDEAL: Buscar a instância vinculada ao Operador logado
        // String instanciaWhatsApp = revendedorService.buscarPorEmail(operador).getInstanciaWhatsapp();
        // Por enquanto, vou deixar null para ele usar a global, ou você pode substituir pela lógica acima
        String instanciaWhatsApp = null;

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
        whatsAppService.enviarMensagemTexto(lead, textoIntro.toString(), operador, instanciaWhatsApp);

        if (lead.getItens() != null && !lead.getItens().isEmpty()) {
            for (var item : lead.getItens()) {
                var produto = item.getProduto();

                if (produto.getImagens() != null && !produto.getImagens().isEmpty()) {
                    try {
                        String objectName = produto.getImagens().get(0);
                        String path = produto.getImagens().get(0);
                        String legendaDaFoto = "💍 *" + produto.getNome() + "*";

                        // Chama o envio de mídia (Passando a instância)
                        whatsAppService.enviarMensagemImagem(modelMapper.map(lead, Lead.class), legendaDaFoto, path, operador, instanciaWhatsApp);

                    } catch (Exception e) {
                        System.out.println("Erro ao agendar imagem: " + e.getMessage());
                    }
                } else {
                    String textoSemFoto = "💍 *" + produto.getNome() + "* (Imagem indisponível)";
                    // Passando a instância
                    whatsAppService.enviarMensagemTexto(lead, textoSemFoto, operador, instanciaWhatsApp);
                }
            }
        }

        return ResponseEntity.ok("Mensagem enfileirada com sucesso para " + lead.getNome());
    }

    public record MensagemRequestDTO(Long leadId) {}
}