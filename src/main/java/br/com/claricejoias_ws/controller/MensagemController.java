package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.exceptions.RegraNegocioException; // 👇 IMPORTANTE: Importe a sua exceção!
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.LeadRepository;
import br.com.claricejoias_ws.service.AutenticacaoService;
import br.com.claricejoias_ws.service.LeadService;
import br.com.claricejoias_ws.service.MinioService;
import br.com.claricejoias_ws.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

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

        // 1. Monta o texto de introdução
        StringBuilder textoIntro = new StringBuilder();
        textoIntro.append("Olá, ").append(lead.getNome()).append(". ");
        textoIntro.append("Aqui é da equipe de atendimento da Clarice Joias.\n\n");

        if (lead.getItens() != null && !lead.getItens().isEmpty()) {
            textoIntro.append("Notamos o seu excelente gosto por nossas peças e vimos que alguns itens estão aguardando em seu carrinho!\n");
        } else {
            textoIntro.append("Vimos que você demonstrou interesse em nossas coleções.\n");
        }

        textoIntro.append("Temos uma condição exclusiva liberada para você finalizar seu pedido hoje. Gostaria de conferir as opções?");

        // 2. Dispara a mensagem de texto inicial
        whatsAppService.enviarMensagemTexto(lead, textoIntro.toString(), autenticacaoService.getUsername());

        // 3. Loop para enviar cada produto do carrinho como uma IMAGEM
        if (lead.getItens() != null && !lead.getItens().isEmpty()) {
            for (var item : lead.getItens()) {
                var produto = item.getProduto();

                // ... dentro do loop dos itens ...
                if (produto.getImagens() != null && !produto.getImagens().isEmpty()) {
                    try {
                        String objectName = produto.getImagens().get(0);

                        // Pega a imagem em Base64 em vez da URL
                        String base64Imagem = minioService.getImagemBase64(objectName);

                        // Algumas APIs exigem o cabeçalho 'data:image/jpeg;base64,' antes do código.
                        // Se as fotos da Clarice Joias forem sempre JPG/JPEG, você pode fixar assim:
                        String mediaBase64 = "data:image/jpeg;base64," + base64Imagem;

                        String legendaDaFoto = "💍 *" + produto.getNome() + "*";
                        String path = produto.getImagens().get(0);

                        // Chama o envio de mídia passando o Base64
                        whatsAppService.enviarMensagemImagem(modelMapper.map(lead,Lead.class) , legendaDaFoto, path, autenticacaoService.getUsername());

                    } catch (Exception e) {
                        System.out.println("Erro ao converter imagem do MinIO: " + e.getMessage());
                    }
                } else {
                    // Opcional: Se o produto não tiver foto, você pode mandar só o nome como texto
                    String textoSemFoto = "💍 *" + produto.getNome() + "* (Imagem indisponível)";
                    whatsAppService.enviarMensagemTexto(lead, textoSemFoto, autenticacaoService.getUsername());
                }
            }
        }

        return ResponseEntity.ok("Mensagem disparada com sucesso para " + lead.getNome());
    }

    public record MensagemRequestDTO(Long leadId) {}
}