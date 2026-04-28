package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.dto.LeadItemDTO;
import br.com.claricejoias_ws.dto.LeadRequestDTO;
import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Carrinho;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.model.LeadItem;
import br.com.claricejoias_ws.repository.CarrinhoRepository; // 👈 Novo import
import br.com.claricejoias_ws.repository.LeadRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository repository;
    private final KeycloakUserService keycloakUserService;
    private final ModelMapper modelMapper;

    // Injetamos o repositório do carrinho para ler direto do banco
    private final CarrinhoRepository carrinhoRepository;

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

        if (whatsappLimpo.length() != 11) {
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
    // ETAPA 2: CAPTAÇÃO VIA CHECKOUT / CADASTRO
    // ==========================================

    // ==========================================
    // ETAPA 2: CAPTAÇÃO VIA CHECKOUT / CADASTRO
    // ==========================================

    @Transactional
    public Lead processarNovoLead(LeadRequestDTO dto, String visitorId) {

        // INTEGRAÇÃO COM KEYCLOAK
        if (dto.isCriarConta() && dto.getSenha() != null && !dto.getSenha().trim().isEmpty()) {
            // Cria o Cliente no banco local e vincula o histórico do computador!
            keycloakUserService.criarUsuarioCliente(dto.getEmail(), dto.getSenha(), dto.getNome(), dto.getWhatsapp(), visitorId);
        }

        // BUSCA OU CRIAÇÃO DO LEAD (Via WhatsApp)
        Optional<Lead> leadExistente = repository.findByWhatsapp(dto.getWhatsapp());
        Lead lead;

        if (leadExistente.isPresent()) {
            // Atualiza o lead que já existia (ex: alguém que baixou o e-book antes de comprar)
            lead = leadExistente.get();
            lead.setNome(dto.getNome());
            lead.setEmail(dto.getEmail());
            lead.setAtivo(true);
            lead.setComprou(false);

            // Atualiza o rastro de navegação caso ele esteja usando outro PC/Celular agora
            if (visitorId != null) {
                lead.setVisitorId(visitorId);
            }

            // 🚨 NOTA: Não precisamos mais limpar ou atualizar a lista de itens aqui,
            // pois o painel vai ler isso direto da tabela de Carrinho em tempo real!

        } else {
            // Cria um lead totalmente novo
            lead = new Lead();
            lead.setWhatsapp(dto.getWhatsapp());
            lead.setNome(dto.getNome());
            lead.setEmail(dto.getEmail());
            lead.setAtivo(true);
            lead.setComprou(false);
            lead.setVisitorId(visitorId);
        }

        // SALVAR NO BANCO
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
        // 1. Busca a página de leads do banco
        Page<Lead> leadsPage = repository.findAll(pageable);

        // 2. Mapeia cada Lead para LeadDTO e busca o carrinho em tempo real
        return leadsPage.map(lead -> {
            LeadDTO dto = modelMapper.map(lead, LeadDTO.class);

            // Tenta achar o carrinho atual da pessoa
            Optional<Carrinho> carrinhoDoLead = Optional.empty();

            if (lead.getUsuarioId() != null) {
                carrinhoDoLead = carrinhoRepository.findFirstByUsuarioId(lead.getUsuarioId());
            }
            if (carrinhoDoLead.isEmpty() && lead.getVisitorId() != null) {
                carrinhoDoLead = carrinhoRepository.findFirstByVisitorId(lead.getVisitorId());
            }

            // Se achou um carrinho, pega os produtos e coloca no DTO para o FrontEnd ver!
            carrinhoDoLead.ifPresent(carrinho -> {
                // Aqui estou assumindo que o seu LeadDTO tem uma lista que aceita esses dados.
                // Ajuste os nomes dos "setters" conforme estiver na sua classe DTO.
                List<LeadItemDTO> itensDoCarrinho = carrinho.getItens().stream().map(item -> {
                    LeadItemDTO itemDto = new LeadItemDTO();
                    itemDto.setId(item.getProduto().getId());
                    itemDto.setProduto(modelMapper.map(item.getProduto(), ProdutoDTO.class) );
                    itemDto.setQuantidade(item.getQuantidade());
//                    itemDto.setPreco(item.getProduto().getPreco());
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