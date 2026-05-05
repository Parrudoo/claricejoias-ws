package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.dto.LeadItemDTO;
import br.com.claricejoias_ws.dto.LeadRequestDTO;
import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Carrinho;
import br.com.claricejoias_ws.model.FilaDisparo;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.CarrinhoRepository;
import br.com.claricejoias_ws.repository.FilaDisparoRepository;
import br.com.claricejoias_ws.repository.LeadRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.hibernate.StaleObjectStateException;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository repository;
    private final KeycloakUserService keycloakUserService;
    private final ModelMapper modelMapper;
    private final EvolutionApiService evolutionApiService;
    private final CarrinhoRepository carrinhoRepository;
    private final FilaDisparoRepository filaDisparoRepository;

    // CACHE TEMPORÁRIO PARA OS CÓDIGOS OTP (WhatsApp -> Código)
    private final Map<String, String> otpCache = new ConcurrentHashMap<>();

    // ==========================================
    // ETAPA 1: CAPTAÇÃO VIA ISCA DIGITAL (Guia de Medidas)
    // ==========================================

    public boolean deveMostrarBotaoGuia(String visitorId) {
        if (visitorId == null || visitorId.trim().isEmpty()) {
            return true;
        }
        return repository.findByVisitorId(visitorId).isEmpty();
    }

    @Transactional
    public void converterEmLead(LeadDTO dto, String visitorId) {
        String whatsappLimpo = dto.getWhatsapp().replaceAll("[^0-9]", "");

        if (whatsappLimpo.length() < 10) {
            throw new RegraNegocioException("Número de WhatsApp incompleto. Certifique-se de incluir o DDD e o 9.");
        }

        String idNavegador = (visitorId != null && !visitorId.trim().isEmpty())
                ? visitorId
                : UUID.randomUUID().toString();

        Lead lead = repository.findByVisitorId(idNavegador)
                .orElseGet(() -> {
                    Lead novo = new Lead();
                    novo.setVisitorId(idNavegador);
                    novo.setAtivo(true);
                    novo.setComprou(false);
                    return novo;
                });

        lead.setNome(dto.getNome());
        lead.setWhatsapp(whatsappLimpo);

        if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
            lead.setEmail(dto.getEmail());
        }

        repository.save(lead);
    }

    // ==========================================
    // ETAPA 2: CHECKOUT PREMIUM (VERIFICAÇÃO OTP)
    // ==========================================

    // PASSO A: Gera o código e envia via Evolution API
    public void solicitarCodigoOtp(String whatsapp) {
        String whatsappLimpo = whatsapp.replaceAll("[^0-9]", "");

        // 1. Gera o OTP e salva no Cache do servidor (Validade de 5 minutos, por exemplo)
        String otp = String.format("%06d", new Random().nextInt(999999));
        otpCache.put(whatsappLimpo, otp);

        // 2. Monta a mensagem
        String mensagem = String.format("🔒 Seu código de segurança Clarice Joias é: *%s*\n\nNão compartilhe este código com ninguém.", otp);

        // 3. Salva na Fila (Apenas anota no banco, NÃO envia ainda)
        FilaDisparo fila = new FilaDisparo();
        fila.setNumeroDestino(whatsappLimpo); // Se não tiver o Lead ainda, salva só o número
        fila.setTexto(mensagem);
        fila.setTipo("OTP"); // Dica: Prioridade máxima
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());

        filaDisparoRepository.save(fila);
        System.out.println("OTP de " + whatsappLimpo + " entrou na fila de espera.");
    }

    // PASSO B: Valida se o que o cliente digitou bate com a memória
    public boolean validarCodigoOtp(String whatsapp, String codigoInformado) {
        String whatsappLimpo = whatsapp.replaceAll("[^0-9]", "");
        String codigoSalvo = otpCache.get(whatsappLimpo);

        if (codigoSalvo != null && codigoSalvo.equals(codigoInformado)) {
            otpCache.remove(whatsappLimpo); // Limpa o código para não ser usado duas vezes
            return true;
        }
        return false;
    }

    @Transactional
    @Retryable(
            retryFor = {
                    DataIntegrityViolationException.class,
                    ObjectOptimisticLockingFailureException.class,
                    StaleObjectStateException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 150)
    )
    public Lead processarNovoLead(LeadRequestDTO dto, String visitorId) {
        String whatsappLimpo = dto.getWhatsapp().replaceAll("[^0-9]", "");

        // 1. Cria a conta no Keycloak (se ele não estiver logado já)
        if (dto.isCriarConta()) {
            // Cria uma senha fixa aleatória para ele poder logar depois no painel
            String senhaAleatoria = String.format("%06d", new Random().nextInt(999999));
            String emailKeycloak = whatsappLimpo + "@claricejoias.com.br";


                keycloakUserService.criarUsuarioCliente(emailKeycloak, senhaAleatoria, dto.getNome(), whatsappLimpo, visitorId);


            // Envia WhatsApp de Sucesso com a senha gerada
            String mensagemConta = String.format(
                    "Olá *%s*! 💎 Recebemos seu pedido!\n\n" +
                            "Para acompanhar o status depois, criamos um acesso rápido para você:\n" +
                            "👤 Usuário: *%s*\n🔑 Senha: *%s*\n\n" +
                            "Nossa equipe já vai te atender por aqui para finalizar os detalhes da forma de pagamento escolhida!",
                    dto.getNome(), whatsappLimpo, senhaAleatoria
            );
            evolutionApiService.enviarMensagemTexto(whatsappLimpo, mensagemConta);

        } else {
            // Se ele já estava logado, só avisa do pedido
            String mensagemPedido = String.format(
                    "Olá *%s*! 💎 Recebemos seu pedido com sucesso!\n\n" +
                            "Nossa equipe já vai te atender por aqui para finalizar os detalhes!",
                    dto.getNome()
            );
            evolutionApiService.enviarMensagemTexto(whatsappLimpo, mensagemPedido);
        }

        // 2. Salva o Lead no Banco de Dados
        Optional<Lead> leadExistente = repository.findByWhatsapp(whatsappLimpo);
        Lead lead;

        if (leadExistente.isPresent()) {
            lead = leadExistente.get();
            lead.setNome(dto.getNome());
            lead.setAtivo(true);
            lead.setComprou(false);
            if (visitorId != null) {
                lead.setVisitorId(visitorId);
            }
        } else {
            lead = new Lead();
            lead.setWhatsapp(whatsappLimpo);
            lead.setNome(dto.getNome());
            lead.setAtivo(true);
            lead.setComprou(false);
            lead.setVisitorId(visitorId);
        }

        return repository.save(lead);
    }

    // ==========================================
    // ETAPA 3: PAINEL ADMINISTRATIVO (CRUD)
    // ==========================================

    @Transactional
    public Lead salvar(Lead lead) {
        return repository.save(lead);
    }

    public Page<LeadDTO> listarTodos(Pageable pageable) {
        Page<Lead> leadsPage = repository.findAll(pageable);

        return leadsPage.map(lead -> {
            LeadDTO dto = modelMapper.map(lead, LeadDTO.class);
            Optional<Carrinho> carrinhoDoLead = Optional.empty();

            if (lead.getUsuarioId() != null) {
                carrinhoDoLead = carrinhoRepository.findFirstByUsuarioId(lead.getUsuarioId());
            }
            if (carrinhoDoLead.isEmpty() && lead.getVisitorId() != null) {
                carrinhoDoLead = carrinhoRepository.findFirstByVisitorId(lead.getVisitorId());
            }

            carrinhoDoLead.ifPresent(carrinho -> {
                List<LeadItemDTO> itensDoCarrinho = carrinho.getItens().stream().map(item -> {
                    LeadItemDTO itemDto = new LeadItemDTO();
                    itemDto.setId(item.getProduto().getId());
                    itemDto.setProduto(modelMapper.map(item.getProduto(), ProdutoDTO.class));
                    itemDto.setQuantidade(item.getQuantidade());
                    return itemDto;
                }).toList();
                dto.setItens(itensDoCarrinho);
            });

            return dto;
        });
    }

    @Transactional
    public Lead alternarStatus(Long id) {
        return repository.findById(id).map(lead -> {
            lead.setAtivo(!lead.getAtivo());
            return repository.save(lead);
        }).orElseThrow(() -> new RegraNegocioException("Lead não encontrado com o ID: " + id));
    }

    @Transactional
    public Lead marcarComoComprado(Long id) {
        return repository.findById(id).map(lead -> {
            lead.setComprou(true);
            return repository.save(lead);
        }).orElseThrow(() -> new RuntimeException("Lead não encontrado com o ID: " + id));
    }
}