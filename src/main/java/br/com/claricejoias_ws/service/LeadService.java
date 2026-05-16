package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.*;
import br.com.claricejoias_ws.enums.StatusDisparo;
import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.*;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository repository;
    private final KeycloakUserService keycloakUserService;
    private final ModelMapper modelMapper;
    private final EvolutionApiService evolutionApiService;
    private final FilaDisparoRepository filaDisparoRepository;
    private final PedidoRepository pedidoRepository;
    private final CarrinhoService carrinhoService;

    private final Map<String, String> otpCache = new ConcurrentHashMap<>();

    // ==========================================
    // ETAPA 1: CAPTAÇÃO VIA ISCA DIGITAL
    // ==========================================

    public boolean deveMostrarBotaoGuia(String visitorId) {
        if (visitorId == null || visitorId.trim().isEmpty()) {
            return true;
        }
        return !repository.existsByVisitorId(visitorId);
    }

    @Transactional
    public String converterEmLead(LeadDTO dto, String visitorId) {
        String whatsappLimpo = dto.getWhatsapp().replaceAll("[^0-9]", "");

        if (whatsappLimpo.length() < 10) {
            throw new RegraNegocioException("Número de WhatsApp incompleto.");
        }

        String idNavegador = (visitorId != null && !visitorId.trim().isEmpty()) ? visitorId : UUID.randomUUID().toString();

        Lead lead = repository.findFirstByVisitorIdOrderByIdDesc(idNavegador).orElse(null);

        if (lead != null && lead.getUsuarioId() != null) {
            lead = null; // Proteção para computador público
        }

        if (lead == null) {
            lead = new Lead();
            lead.setVisitorId(idNavegador);
            lead.setAtivo(true);
            lead.setComprou(false);
        }

        lead.setNome(dto.getNome());
        lead.setWhatsapp(whatsappLimpo);
        if (dto.getEmail() != null) lead.setEmail(dto.getEmail());

        repository.save(lead);

        // ADICIONE ESTA LINHA: Retorna o código do cupom que o cliente ganhou
        return "CLARICE20";
    }

    // ==========================================
    // ETAPA 2: CHECKOUT (OTP E CONVERSÃO)
    // ==========================================

    public void solicitarCodigoOtp(String whatsapp) {
        String whatsappLimpo = whatsapp.replaceAll("[^0-9]", "");
        String otp = String.format("%06d", new Random().nextInt(999999));
        otpCache.put(whatsappLimpo, otp);

        String mensagem = String.format("🔒 Seu código de segurança Clarice Joias é: *%s*", otp);

        FilaDisparo fila = new FilaDisparo();
        fila.setNumeroDestino(whatsappLimpo);
        fila.setTexto(mensagem);
        fila.setTipo("OTP");
        fila.setStatus(StatusDisparo.PENDENTE);
        fila.setDataCriacao(LocalDateTime.now());

        filaDisparoRepository.save(fila);
    }

    public boolean validarCodigoOtp(String whatsapp, String codigoInformado) {
        String whatsappLimpo = whatsapp.replaceAll("[^0-9]", "");
        String codigoSalvo = otpCache.get(whatsappLimpo);

        if (codigoSalvo != null && codigoSalvo.equals(codigoInformado)) {
            otpCache.remove(whatsappLimpo);
            return true;
        }
        return false;
    }

    @Transactional
    @Retryable(
            retryFor = {DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class, StaleObjectStateException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 150)
    )
    public Lead processarNovoLead(LeadRequestDTO dto, String visitorId, String usuarioIdOrigem, String revendedorId) {
        String whatsappLimpo = dto.getWhatsapp().replaceAll("[^0-9]", "");
        boolean estaLogado = (usuarioIdOrigem != null && !usuarioIdOrigem.trim().isEmpty());
        boolean isLojaMatriz = (revendedorId == null || revendedorId.trim().isEmpty());

        String finalUsuarioId = usuarioIdOrigem;
        String senhaGerada = null;

        // 1. Cria a conta se necessário (Enviando a loja para o KeycloakService)
        if (dto.isCriarConta() && !estaLogado) {
            senhaGerada = String.format("%06d", new Random().nextInt(999999));
            String emailKeycloak = whatsappLimpo + "@claricejoias.com.br";

            finalUsuarioId = keycloakUserService.criarUsuarioCliente(emailKeycloak, senhaGerada, dto.getNome(), whatsappLimpo, revendedorId);
        }

        // 2. BUSCA INTELIGENTE DO LEAD (Agora isolada por Loja/Revendedor)
        Lead lead = obterOuPromoverLeadSeguro(whatsappLimpo, visitorId, finalUsuarioId, revendedorId);

        // Atualiza os dados
        lead.setWhatsapp(whatsappLimpo);
        lead.setNome(dto.getNome());
        lead.setAtivo(true);
        if (visitorId != null) lead.setVisitorId(visitorId);
        if (finalUsuarioId != null) lead.setUsuarioId(finalUsuarioId);

        // VINCULA O LEAD À LOJA ANTES DE SALVAR
        if (!isLojaMatriz) {
            Revendedor revendedor = new Revendedor();
            revendedor.setId(revendedorId);
            lead.setRevendedor(revendedor);
        }

        lead = repository.save(lead);

        // 3. Busca o "Carrinho" (Usando o revendedorId que já implementamos antes!)
        Pedido carrinhoPedido = carrinhoService.obterOuCriarCarrinho(visitorId, usuarioIdOrigem, revendedorId);

        if (carrinhoPedido.getItens().isEmpty()) {
            throw new RegraNegocioException("O carrinho está vazio.");
        }

        // 4. Atualiza o Pedido
        carrinhoPedido.setLead(lead);
        carrinhoPedido.setStatus(StatusPedido.AGUARDANDO_WHATSAPP);
        carrinhoPedido.setMetodoPagamento(dto.getMetodoPagamento() != null ? dto.getMetodoPagamento() : "PIX");
        carrinhoPedido.setDataAtualizacao(LocalDateTime.now());

        if (finalUsuarioId != null) {
            carrinhoPedido.setUsuarioId(finalUsuarioId);
        }

        pedidoRepository.save(carrinhoPedido);

        // 5. Fluxo de notificações
        enviarNotificacaoFinal(dto, lead, carrinhoPedido, whatsappLimpo, senhaGerada);

        return lead;
    }

    // =======================================================================
// O MÉTODO SEGURO AGORA VERIFICA A LOJA
// =======================================================================
    private Lead obterOuPromoverLeadSeguro(String whatsapp, String visitorId, String usuarioId, String revendedorId) {
        boolean isLojaMatriz = (revendedorId == null || revendedorId.trim().isEmpty());

        // 1. Prioridade Máxima: O WhatsApp DENTRO DA MESMA LOJA
        Optional<Lead> leadPorWhatsapp = isLojaMatriz
                ? repository.findByWhatsappAndRevendedorIsNull(whatsapp)
                : repository.findByWhatsappAndRevendedorId(whatsapp, revendedorId);

        if (leadPorWhatsapp.isPresent()) {
            return leadPorWhatsapp.get();
        }

        // 2. Rastro anônimo DENTRO DA MESMA LOJA
        if (visitorId != null && !visitorId.trim().isEmpty()) {
            Lead leadDoNavegador = isLojaMatriz
                    ? repository.findFirstByVisitorIdAndRevendedorIsNullOrderByIdDesc(visitorId).orElse(null)
                    : repository.findFirstByVisitorIdAndRevendedorIdOrderByIdDesc(visitorId, revendedorId).orElse(null);

            if (leadDoNavegador != null) {
                // A TRAVA DO COMPUTADOR PÚBLICO
                if (leadDoNavegador.getUsuarioId() != null && !leadDoNavegador.getUsuarioId().equals(usuarioId)) {
                    return new Lead();
                } else {
                    return leadDoNavegador;
                }
            }
        }

        // 3. Se não achou, nasce um novo zerado
        return new Lead();
    }

    // Método de notificação ajustado para usar a 'senhaGerada' e saber se envia ou não as credenciais
    private void enviarNotificacaoFinal(LeadRequestDTO dto, Lead lead, Pedido pedido, String whatsapp, String senhaGerada) {
        if (senhaGerada != null) {
            String msg = String.format("Olá *%s*! 💎 Pedido *#%d* recebido!\n👤 Usuário: *%s*\n🔑 Senha: *%s*",
                    dto.getNome(), pedido.getId(), whatsapp, senhaGerada);
            evolutionApiService.enviarMensagemTexto(whatsapp, msg);
        } else {
            String msg = String.format("Olá *%s*! 💎 Recebemos seu pedido *#%d* com sucesso!", dto.getNome(), pedido.getId());
            evolutionApiService.enviarMensagemTexto(whatsapp, msg);
        }
    }


    private Lead obterOuPromoverLeadSeguro(String whatsapp, String visitorId, String usuarioId) {
        // 1. Prioridade Máxima: O WhatsApp (Garante que é a mesma pessoa, independente do PC)
        Optional<Lead> leadPorWhatsapp = repository.findByWhatsapp(whatsapp);
        if (leadPorWhatsapp.isPresent()) {
            return leadPorWhatsapp.get();
        }

        // 2. Se o WhatsApp é novo, vamos ver se temos um rastro anônimo neste navegador
        if (visitorId != null && !visitorId.trim().isEmpty()) {
            Lead leadDoNavegador = repository.findFirstByVisitorIdOrderByIdDesc(visitorId).orElse(null);

            if (leadDoNavegador != null) {
                // =========================================================
                //  A TRAVA DO COMPUTADOR PÚBLICO
                // =========================================================
                if (leadDoNavegador.getUsuarioId() != null && !leadDoNavegador.getUsuarioId().equals(usuarioId)) {
                    // CENÁRIO: Outra pessoa já logou ou criou conta neste PC antes!
                    // Ação: Ignoramos o rastro antigo para não misturar os dados e criamos um novo.
                    return new Lead();
                } else {
                    // CENÁRIO: É apenas um visitante anônimo que acabou de decidir comprar/criar conta.
                    // Ação: "Promovemos" este Lead, mantendo o histórico dele.
                    return leadDoNavegador;
                }
            }
        }

        // 3. Se não achou WhatsApp e não tem rastro válido, cria um do zero.
        return new Lead();
    }


    // ==========================================
    // ETAPA 3: PAINEL ADMINISTRATIVO (CRUD)
    // ==========================================

    public Page<LeadDTO> listarTodos(Pageable pageable) {
        return repository.findAll(pageable).map(this::montarLeadDTO);
    }

    public LeadDTO buscarPorId(Long id) {
        Lead lead = repository.findById(id).orElseThrow(() -> new RegraNegocioException("Lead não encontrado"));
        return montarLeadDTO(lead);
    }

    private LeadDTO montarLeadDTO(Lead lead) {
        if (lead == null) return null;

        // 1. Instanciamos o DTO e mapeamos os campos básicos manualmente
        LeadDTO dto = new LeadDTO();
        dto.setId(lead.getId());
        dto.setNome(lead.getNome());
        dto.setWhatsapp(lead.getWhatsapp());
        dto.setEmail(lead.getEmail());
        dto.setAtivo(lead.getAtivo());
        dto.setComprou(lead.getComprou());

        // 2. Mapeamos os Itens (extraindo do Pedido que é um Carrinho) e Agrupamos
        if (lead.getPedidos() != null) {
            lead.getPedidos().stream()
                .filter(p -> p.getStatus() != null &&
                        (p.getStatus().equals(StatusPedido.CARRINHO) || p.getStatus().equals(StatusPedido.CARRINHO_ABANDONADO)))
                    .findFirst() // Pegamos o carrinho
                    .ifPresent(pedido -> {

                        // Usamos Collectors.toMap para agrupar pelo ID do Produto
                        List<LeadItemDTO> itensDTO = new ArrayList<>(pedido.getItens().stream()
                                .filter(item -> item.getProduto() != null) // Prevenção de segurança
                                .collect(Collectors.toMap(
                                        item -> item.getProduto().getId(), // Chave: ID do Produto
                                        item -> { // Valor: Construção do LeadItemDTO inicial
                                            LeadItemDTO itemDto = new LeadItemDTO();
                                            itemDto.setId(item.getProduto().getId());
                                            itemDto.setQuantidade(item.getQuantidade());
                                            itemDto.setPrecoMomento(item.getPrecoUnitario());
                                            Produto prod = item.getProduto();
                                            ProdutoDTO prodDto = new ProdutoDTO();
                                            prodDto.setId(prod.getId());
                                            prodDto.setNome(prod.getNome());
                                            prodDto.setPreco(item.getPrecoUnitario()); // Mantemos o preço unitário
                                            prodDto.setImagens(prod.getImagens());

                                            itemDto.setProduto(prodDto);
                                            return itemDto;
                                        },
                                        (itemExistente, itemRepetido) -> {
                                            // Regra de Conflito: O que fazer se achar produtos iguais?
                                            // Somamos a quantidade do item repetido ao item que já existia no map
                                            itemExistente.setQuantidade(itemExistente.getQuantidade() + itemRepetido.getQuantidade());
                                            return itemExistente;
                                        }
                                )).values()); // Pegamos apenas os valores resultantes

                        dto.setItens(itensDTO);
                    });
        }

        // 3. Mapeamos o histórico de disparos manualmente
        if (lead.getHistoricoDisparos() != null) {
            List<HistoricoDisparoDTO> disparosDTO = lead.getHistoricoDisparos().stream().map(h -> {
                HistoricoDisparoDTO hDto = new HistoricoDisparoDTO();
                hDto.setId(h.getId());
//            hDto.setMensagem(h.getMensagem());
                hDto.setDataHoraDisparo(h.getDataHoraDisparo());
//            hDto.setTipo(h.getTipo());
                return hDto;
            }).collect(Collectors.toList());

            dto.setHistoricoDisparos(disparosDTO);
        }

        return dto;
    }

    @Transactional
    public Lead marcarComoComprado(Long leadId) {
        Lead lead = repository.findById(leadId).orElseThrow(() -> new RuntimeException("Lead não encontrado"));

        // Busca o pedido que era o carrinho e marca como pago
        pedidoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(lead.getVisitorId(), StatusPedido.CARRINHO)
                .ifPresent(p -> {
                    p.setStatus(StatusPedido.PAGO);
                    pedidoRepository.save(p);
                });

        lead.setComprou(true);
        return repository.save(lead);
    }

    @Transactional
    public Lead alternarStatus(Long id) {
        return repository.findById(id).map(lead -> {
            lead.setAtivo(!lead.getAtivo());
            return repository.save(lead);
        }).orElseThrow(() -> new RegraNegocioException("Lead não encontrado com o ID: " + id));
    }
}