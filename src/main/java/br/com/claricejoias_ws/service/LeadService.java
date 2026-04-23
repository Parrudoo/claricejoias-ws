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
    public Lead processarNovoLead(LeadRequestDTO dto) {

        // ==========================================
        // ETAPA 1: INTEGRAÇÃO COM KEYCLOAK
        // ==========================================
        if (dto.isCriarConta() && dto.getSenha() != null && !dto.getSenha().trim().isEmpty()) {
            keycloakUserService.criarUsuarioCliente(dto.getEmail(), dto.getSenha(), dto.getNome());
        }

        // ==========================================
        // ETAPA 2: BUSCA OU CRIAÇÃO DO LEAD
        // ==========================================
        // Busca no banco de dados se já existe um Lead com esse WhatsApp
        Optional<Lead> leadExistente = repository.findByWhatsapp(dto.getWhatsapp());
        Lead lead;

        if (leadExistente.isPresent()) {
            // Se existir, nós vamos apenas atualizar os dados
            lead = leadExistente.get();
            lead.setNome(dto.getNome());
            lead.setEmail(dto.getEmail());
            lead.setAtivo(true); // Reativa o lead, caso estivesse inativo
            lead.setComprou(false); // Reseta a compra, já que é uma nova tentativa de checkout

            // Limpa os itens antigos do carrinho abandonado anterior
            if (lead.getItens() != null) {
                lead.getItens().clear();
            }
        } else {
            // Se não existir, instanciamos um novo
            lead = new Lead();
            lead.setWhatsapp(dto.getWhatsapp());
            lead.setNome(dto.getNome());
            lead.setEmail(dto.getEmail());
            lead.setAtivo(true);
            lead.setComprou(false);
        }

        // ==========================================
        // ETAPA 3: POPULAR OS ITENS DO CARRINHO
        // ==========================================
        if (dto.getItens() != null && !dto.getItens().isEmpty()) {
            dto.getItens().forEach(itemDto -> {
                // Busca o produto real no banco para fazer a ligação
                Optional<Produto> produtoOpt = produtoService.buscarPorId(itemDto.getId());

                if (produtoOpt.isPresent()) {
                    LeadItem leadItem = new LeadItem();
                    leadItem.setProduto(produtoOpt.get());
                    leadItem.setQuantidade(itemDto.getQuantidade());
                    leadItem.setPrecoMomento(itemDto.getPreco());
                    lead.addItem(leadItem); // Vincula o novo item ao lead
                }
            });
        }

        // ==========================================
        // ETAPA 4: SALVAR NO BANCO
        // ==========================================
        return repository.save(lead);
    }
}