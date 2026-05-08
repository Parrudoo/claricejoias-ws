package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.dto.LeadItemDTO;
import br.com.claricejoias_ws.dto.LeadRequestDTO;
import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.enums.StatusCarrinho;
import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.*;
import br.com.claricejoias_ws.repository.CarrinhoRepository;
import br.com.claricejoias_ws.repository.FilaDisparoRepository;
import br.com.claricejoias_ws.repository.LeadRepository;
import br.com.claricejoias_ws.repository.PedidoRepository;
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

import java.math.BigDecimal;
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
    private final PedidoRepository pedidoRepository;
    private final CarrinhoService carrinhoService;

    // CACHE TEMPORÁRIO PARA OS CÓDIGOS OTP (WhatsApp -> Código)
    private final Map<String, String> otpCache = new ConcurrentHashMap<>();

    // ==========================================
    // ETAPA 1: CAPTAÇÃO VIA ISCA DIGITAL (Guia de Medidas)
    // ==========================================

    public boolean deveMostrarBotaoGuia(String visitorId) {
        if (visitorId == null || visitorId.trim().isEmpty()) {
            return true;
        }
        return !repository.existsByVisitorId(visitorId);
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

        Lead lead = null;

        // 1. Busca segura pelo Lead mais recente deste navegador
        if (visitorId != null && !visitorId.trim().isEmpty()) {
            lead = repository.findFirstByVisitorIdOrderByIdDesc(idNavegador).orElse(null);

            // 2. A TRAVA DO COMPUTADOR PÚBLICO
            // Se o lead encontrado já pertence a uma conta oficial (tem usuarioId),
            // nós não podemos sobrescrever os dados dele. Anulamos para forçar a criação de um novo.
            if (lead != null && lead.getUsuarioId() != null) {
                lead = null;
            }
        }

        // 3. Criação ou Atualização
        if (lead == null) {
            lead = new Lead();
            // Mantemos o idNavegador para não perder o vínculo com o carrinho atual!
            lead.setVisitorId(idNavegador);
            lead.setAtivo(true);
            lead.setComprou(false);
        }

        // Atualiza os dados capturados na landing page / e-book
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
    public Lead processarNovoLead(LeadRequestDTO dto, String visitorId, String usuarioId) {
        String whatsappLimpo = dto.getWhatsapp().replaceAll("[^0-9]", "");
        boolean estaLogado = (usuarioId != null && !usuarioId.isEmpty());

        // =========================================================
        // 1. SALVA O LEAD PRIMEIRO (Para o pedido ter a quem pertencer)
        // =========================================================
        Lead lead = repository.findByWhatsapp(whatsappLimpo).orElseGet(Lead::new);
        lead.setWhatsapp(whatsappLimpo);
        lead.setNome(dto.getNome());
        lead.setAtivo(true);
        lead.setComprou(false);

        if (visitorId != null) lead.setVisitorId(visitorId);
        if (estaLogado) lead.setUsuarioId(usuarioId);

        lead = repository.save(lead); // Salva e gera o ID do Lead

        // =========================================================
        // 2. CONVERTE O CARRINHO EM PEDIDO
        // =========================================================
        Carrinho carrinho = carrinhoService.obterOuCriarCarrinho(visitorId, usuarioId);

        if (carrinho.getItens().isEmpty()) {
            throw new RegraNegocioException("Não é possível processar a compra: O carrinho está vazio.");
        }

        Pedido novoPedido = new Pedido();
        novoPedido.setLead(lead);
        novoPedido.setVisitorId(visitorId);
        novoPedido.setUsuarioId(usuarioId);

        // Certifique-se de que o seu LeadRequestDTO tenha o campo formaPagamento que vem do React!
//        novoPedido.setFormaPagamento(dto.getFormaPagamento());
        novoPedido.setStatusPedido(StatusPedido.AGUARDANDO_WHATSAPP);

        BigDecimal total = BigDecimal.ZERO;

        // Transfere os itens congelando o preço
        for (ItemCarrinho ic : carrinho.getItens()) {
            ItemPedido ip = new ItemPedido();
            ip.setProduto(ic.getProduto());
            ip.setQuantidade(ic.getQuantidade());
            ip.setPrecoUnitario(ic.getProduto().getPreco()); // Congela o preço

            novoPedido.addItem(ip);
            total = total.add(ip.getPrecoUnitario().multiply(BigDecimal.valueOf(ip.getQuantidade())));
        }

        novoPedido.setTotalCobrado(total);
        pedidoRepository.save(novoPedido); // Salva e gera o ID do Pedido

        // =========================================================
        // 3. LIMPA O CARRINHO
        // =========================================================
        carrinhoService.limparCarrinho(visitorId, usuarioId);

        // =========================================================
        // 4. NOTIFICAÇÕES (AGORA COM O NÚMERO DO PEDIDO!)
        // =========================================================
        if (dto.isCriarConta() && !estaLogado) {
            String senhaAleatoria = String.format("%06d", new Random().nextInt(999999));
            String emailKeycloak = whatsappLimpo + "@claricejoias.com.br";

            keycloakUserService.criarUsuarioCliente(emailKeycloak, senhaAleatoria, dto.getNome(), whatsappLimpo, visitorId);

            String mensagemConta = String.format(
                    "Olá *%s*! 💎 Recebemos seu pedido *#%d*!\n\n" +
                            "Para acompanhar o status depois, criamos um acesso rápido para você:\n" +
                            "👤 Usuário: *%s*\n🔑 Senha: *%s*\n\n" +
                            "Nossa equipe já vai te atender por aqui para finalizar os detalhes da forma de pagamento: *%s*!",
                    dto.getNome(), novoPedido.getId(), whatsappLimpo, senhaAleatoria, "PIX"
            );
            evolutionApiService.enviarMensagemTexto(whatsappLimpo, mensagemConta);

        } else {
            String mensagemPedido = String.format(
                    "Olá *%s*! 💎 Recebemos seu pedido *#%d* com sucesso!\n\n" +
                            "Nossa equipe já vai te atender por aqui para finalizar os detalhes!",
                    dto.getNome(), novoPedido.getId()
            );
            evolutionApiService.enviarMensagemTexto(whatsappLimpo, mensagemPedido);
        }

        return lead;
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
                carrinhoDoLead = carrinhoRepository.findFirstByUsuarioIdAndStatusOrderByIdDesc(lead.getUsuarioId(),StatusCarrinho.ABERTO);
            }
            if (carrinhoDoLead.isEmpty() && lead.getVisitorId() != null) {
                carrinhoDoLead = carrinhoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(lead.getVisitorId(),StatusCarrinho.ABERTO);
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

    public LeadDTO buscarPorId(Long id) {
        // 1. Busca o Lead (objeto simples)
        Lead lead = repository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Lead não encontrado"));

        // 2. Converte o Lead para DTO
        LeadDTO dto = modelMapper.map(lead, LeadDTO.class);

        // 3. Busca o Carrinho
        Optional<Carrinho> carrinhoDoLead = Optional.empty();

        if (lead.getUsuarioId() != null) {
            carrinhoDoLead = carrinhoRepository.findFirstByUsuarioIdAndStatusOrderByIdDesc(lead.getUsuarioId(),StatusCarrinho.ABERTO);
        }
        if (carrinhoDoLead.isEmpty() && lead.getVisitorId() != null) {
            carrinhoDoLead = carrinhoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(lead.getVisitorId(),StatusCarrinho.ABERTO);
        }

        // 4. Preenche os itens se o carrinho existir
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

        // 5. Retorna o DTO preenchido
        return dto;
    }

    @Transactional
    public Lead alternarStatus(Long id) {
        return repository.findById(id).map(lead -> {
            lead.setAtivo(!lead.getAtivo());
            return repository.save(lead);
        }).orElseThrow(() -> new RegraNegocioException("Lead não encontrado com o ID: " + id));
    }

    @Transactional // Garante que se der erro no carrinho, o lead não salva (rollback)
    public Lead marcarComoComprado(Long leadId) {
        // 1. Busca o Lead
        Lead lead = repository.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead não encontrado com o ID: " + leadId));

        Optional<Carrinho> carrinhoAbertoOpt;

        if (lead.getUsuarioId() != null){
          carrinhoAbertoOpt   = carrinhoRepository.findFirstByUsuarioIdAndStatusOrderByIdDesc(lead.getUsuarioId(), StatusCarrinho.ABERTO);
        }else{
            carrinhoAbertoOpt = carrinhoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(lead.getVisitorId(), StatusCarrinho.ABERTO);
        }


        // 3. Se encontrar o carrinho aberto, marca como pago
        if (carrinhoAbertoOpt.isPresent()) {
            Carrinho carrinho = carrinhoAbertoOpt.get();
            carrinho.setStatus(StatusCarrinho.PAGO);
            carrinhoRepository.save(carrinho);
        } else {
            // Aqui você decide a regra: Lança exceção se não tiver carrinho?
            // Ou apenas marca o lead como comprado assim mesmo?
            // throw new RuntimeException("Nenhum carrinho em aberto encontrado para o Lead.");
        }

        // 4. Atualiza o Lead (você vai precisar adicionar o campo 'comprou' na entidade Lead)
        lead.setComprou(true);
        return repository.save(lead);
    }
}