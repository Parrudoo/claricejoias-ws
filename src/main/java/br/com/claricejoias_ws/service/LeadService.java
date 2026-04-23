package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.LeadDTO;
import br.com.claricejoias_ws.dto.LeadRequestDTO;
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

        // O próprio objeto Page tem um método map() super limpo!
        return leadsPage.map(lead -> modelMapper.map(lead, LeadDTO.class));
    }

    public Lead alternarStatus(Long id) {
        return repository.findById(id).map(lead -> {
            lead.setAtivo(!lead.getAtivo()); // Inverte o status atual
            return repository.save(lead);
        }).orElseThrow(() -> new RuntimeException("Lead não encontrado com o ID: " + id));
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
        // ETAPA 2: MAPEAMENTO DO DTO PARA ENTIDADE
        // ==========================================
        Lead lead = new Lead();
        lead.setNome(dto.getNome());
        lead.setWhatsapp(dto.getWhatsapp());
        lead.setEmail(dto.getEmail());
        lead.setAtivo(true);
        lead.setComprou(false);

        if (dto.getItens() != null && !dto.getItens().isEmpty()) {
            dto.getItens().forEach(itemDto -> {
                // Busca o produto real no banco para fazer a ligação
                Optional<Produto> produtoOpt = produtoService.buscarPorId(itemDto.getId());

                if (produtoOpt.isPresent()) {
                    LeadItem leadItem = new LeadItem();
                    leadItem.setProduto(produtoOpt.get());
                    leadItem.setQuantidade(itemDto.getQuantidade());
                    leadItem.setPrecoMomento(itemDto.getPreco());
                    lead.addItem(leadItem); // Vincula ao lead
                }
            });
        }

        // ==========================================
        // ETAPA 3: SALVAR NO BANCO
        // ==========================================
        return repository.save(lead);
    }
}