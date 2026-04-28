package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.dto.LeadRequestDTO;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.model.LeadItem;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.repository.LeadRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository repository;
    private final KeycloakUserService keycloakUserService;
    private final ProdutoService produtoService;
    private final ModelMapper modelMapper;


    @Transactional
    public Lead salvar(Lead lead) {
        return repository.save(lead);
    }

    public Page<LeadDTO> listarTodos(Pageable pageable) {
        // Busca a página de entidades do banco
        Page<Lead> leadsPage = repository.findAll(pageable);
        Page<LeadDTO> leadDTOS =  leadsPage.map(lead -> modelMapper.map(lead, LeadDTO.class));

        return leadDTOS;
    }

    public Lead alternarStatus(Long id) {
        return repository.findById(id).map(lead -> {
            lead.setAtivo(!lead.getAtivo()); // Inverte o status atual
            return repository.save(lead);
        }).orElseThrow(() -> new RegraNegocioException("Lead não encontrado com o ID: " + id));
    }

    public Lead marcarComoComprado(Long id) {
        return repository.findById(id).map(lead -> {
            lead.setComprou(true);
            return repository.save(lead);
        }).orElseThrow(() -> new RuntimeException("Lead não encontrado com o ID: " + id));
    }

    @Transactional
    public Lead processarNovoLead(LeadRequestDTO dto, String visitorId) { // Recebendo o visitorId aqui

        // ==========================================
        // ETAPA 1: INTEGRAÇÃO COM KEYCLOAK
        // ==========================================
        if (dto.isCriarConta() && dto.getSenha() != null && !dto.getSenha().trim().isEmpty()) {
            // Agora passamos o visitorId para criar o Cliente no banco local com o histórico!
            keycloakUserService.criarUsuarioCliente(dto.getEmail(), dto.getSenha(), dto.getNome(), visitorId);
        }

        // ==========================================
        // ETAPA 2: BUSCA OU CRIAÇÃO DO LEAD
        // ==========================================
        Optional<Lead> leadExistente = repository.findByWhatsapp(dto.getWhatsapp());
        Lead lead;

        if (leadExistente.isPresent()) {
            lead = leadExistente.get();
            lead.setNome(dto.getNome());
            lead.setEmail(dto.getEmail());
            lead.setAtivo(true);
            lead.setComprou(false);

            // Atualiza o rastro de navegação caso ele esteja usando outro PC/Celular
            if (visitorId != null) {
                lead.setVisitorId(visitorId);
            }

            if (lead.getItens() != null) {
                lead.getItens().clear();
            }
        } else {
            lead = new Lead();
            lead.setWhatsapp(dto.getWhatsapp());
            lead.setNome(dto.getNome());
            lead.setEmail(dto.getEmail());
            lead.setAtivo(true);
            lead.setComprou(false);
            lead.setVisitorId(visitorId); // 👈 Salva de onde esse lead veio
        }

        // ==========================================
        // ETAPA 3: POPULAR OS ITENS DO CARRINHO
        // ==========================================
        if (dto.getItens() != null && !dto.getItens().isEmpty()) {
            dto.getItens().forEach(itemDto -> {
                Optional<Produto> produtoOpt = produtoService.buscarPorId(itemDto.getId());

                if (produtoOpt.isPresent()) {
                    LeadItem leadItem = new LeadItem();
                    leadItem.setProduto(produtoOpt.get());
                    leadItem.setQuantidade(itemDto.getQuantidade());
                    leadItem.setPrecoMomento(itemDto.getPreco());
                    lead.addItem(leadItem);
                }
            });
        }

        // ==========================================
        // ETAPA 4: SALVAR NO BANCO
        // ==========================================
        return repository.save(lead);
    }
}