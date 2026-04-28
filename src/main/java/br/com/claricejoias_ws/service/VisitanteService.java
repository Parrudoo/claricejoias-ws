package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.model.Visitante;
import br.com.claricejoias_ws.repository.LeadRepository;
import br.com.claricejoias_ws.repository.VisitanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitanteService {

    private final VisitanteRepository visitanteRepository;
    private final LeadRepository leadRepository;

    @Transactional
    public String inicializarOuRecuperarVisitante(String uuidDoCookie) {
        if (uuidDoCookie != null) {
            return uuidDoCookie;
        }

        // Se não tem cookie, gera um novo crachá no banco
        String novoUuid = UUID.randomUUID().toString();
        Visitante novoVisitante = new Visitante();
        novoVisitante.setVisitorUuid(novoUuid);
        visitanteRepository.save(novoVisitante);

        return novoUuid;
    }

    @Transactional(readOnly = true)
    public boolean deveMostrarBotaoGuia(String uuid) {
        if (uuid == null) return true;

        return visitanteRepository.findByVisitorUuid(uuid)
                .map(v -> !v.isBaixouGuia())
                .orElse(true);
    }

    @Transactional
    public void vincularVisitanteAoUsuarioLogado(String visitorId, String usuarioId) {
        if (visitorId == null || usuarioId == null) return;

        visitanteRepository.findByVisitorUuid(visitorId).ifPresent(visitante -> {
            // Se o visitante ainda não tem um usuário logado vinculado a ele
            if (visitante.getUsuarioId() == null) {
                visitante.setUsuarioId(usuarioId);
                visitanteRepository.save(visitante);

                // PRONTO! Neste exato momento, o Visitante/Lead virou um CLIENTE no seu banco de dados.
                // Aqui você poderia, no futuro, disparar um email de boas-vindas.
            }
        });
    }

    @Transactional
    public void converterEmLead(LeadDTO dto, String visitorId) {
        // Limpa tudo que não for número
        String whatsappLimpo = dto.getWhatsapp().replaceAll("[^0-9]", "");

        // Validação extra: No Brasil, números com DDD devem ter 11 dígitos
        if (whatsappLimpo.length() != 11) {
            throw new RegraNegocioException("Número de WhatsApp incompleto. Certifique-se de incluir o DDD e o 9.");
        }
        // 1. Salva o Lead oficial para o painel administrativo
        Lead lead = new Lead();
        lead.setVisitorId(visitorId);
        lead.setNome(dto.getNome());
        lead.setWhatsapp(whatsappLimpo);
        lead.setAtivo(true);
        lead.setComprou(false);
        leadRepository.save(lead);

        // 2. Vincula os dados ao Visitante (Crachá) para controle do site
        String uuidFinal = (visitorId != null) ? visitorId : UUID.randomUUID().toString();

        Visitante visitante = visitanteRepository.findByVisitorUuid(uuidFinal)
                .orElseGet(() -> {
                    Visitante v = new Visitante();
                    v.setVisitorUuid(uuidFinal);
                    return v;
                });

        visitante.setNome(dto.getNome());
        visitante.setWhatsapp(dto.getWhatsapp());
        visitante.setBaixouGuia(true);
        visitanteRepository.save(visitante);
    }
}