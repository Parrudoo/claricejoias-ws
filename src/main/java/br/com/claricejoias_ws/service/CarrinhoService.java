
package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.CarrinhoDTO;
import br.com.claricejoias_ws.dto.ItemCarrinhoDTO;
import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.enums.StatusCarrinho;
import br.com.claricejoias_ws.model.Carrinho;
import br.com.claricejoias_ws.model.ItemCarrinho;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.repository.CarrinhoRepository;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.StaleObjectStateException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CarrinhoService {


    private final CarrinhoRepository carrinhoRepository;
    private final ProdutoRepository produtoRepository;
    private final ModelMapper modelMapper;

    /**
     * Ponto de entrada limpo.
     * O @Retryable intercepta automaticamente o erro de concorrência e tenta rodar o método
     * de novo de forma transparente, sem sujar o código com try-catch.
     */
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
    public Carrinho obterOuCriarCarrinho(String visitorId, String usuarioId) {
        if (isUsuarioLogado(usuarioId)) {
            return processarCarrinhoDeUsuario(visitorId, usuarioId);
        }
        return processarCarrinhoAnonimo(visitorId);
    }


    // ========================================================================
    // MÉTODO DE CONSULTA PURA (Não cria lixo no banco)
    // ========================================================================

    @Transactional(readOnly = true)
    public CarrinhoDTO consultarCarrinhoAtual(String visitorId, String usuarioId) {
        Optional<Carrinho> carrinhoOpt = Optional.empty();

        if (isUsuarioLogado(usuarioId)) {
            carrinhoOpt = carrinhoRepository.findFirstByUsuarioIdAndStatusOrderByIdDesc(usuarioId, StatusCarrinho.ABERTO);
        } else if (visitorId != null) {
            carrinhoOpt = carrinhoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(visitorId, StatusCarrinho.ABERTO);
        }

        // Se achou, converte para DTO. Se não, retorna null (que vira 204 no Controller)
        return carrinhoOpt.map(this::convertToDTO).orElse(null);
    }

    // Método auxiliar de conversão (Pode usar ModelMapper aqui se preferir)
    private CarrinhoDTO convertToDTO(Carrinho carrinho) {
        CarrinhoDTO dto = new CarrinhoDTO();
        dto.setId(carrinho.getId());
        dto.setVisitorId(carrinho.getVisitorId());
        dto.setUsuarioId(carrinho.getUsuarioId());

                    List<ItemCarrinhoDTO> itensDTO = carrinho.getItens().stream().map(item -> {
            ItemCarrinhoDTO itemDto = new ItemCarrinhoDTO();
            itemDto.setProduto(modelMapper.map(item.getProduto(), ProdutoDTO.class) );
            itemDto.setQuantidade(item.getQuantidade());



            return itemDto;
        }).toList();

        dto.setItens(itensDTO);

        // Calcula o total do carrinho no servidor
        BigDecimal total = itensDTO.stream()
                .map(i -> i.getProduto().getPreco().multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setValorTotal(total);

        return dto;
    }

    // ========================================================================
    // MÉTODOS PRIVADOS DE DOMÍNIO (CLEAN CODE)
    // ========================================================================

    private boolean isUsuarioLogado(String usuarioId) {
        return usuarioId != null && !usuarioId.trim().isEmpty();
    }

    private Carrinho processarCarrinhoDeUsuario(String visitorId, String usuarioId) {
        // A vinculação do Lead ao Cliente já acontece no Login.
        // Aqui o foco é exclusivamente mesclar os carrinhos!

        // 1. Busca ou cria o carrinho oficial
        Carrinho carrinhoOficial = carrinhoRepository.findFirstByUsuarioIdAndStatusOrderByIdDesc(usuarioId,StatusCarrinho.ABERTO)
                .orElseGet(() -> criarCarrinho(null, usuarioId));

        // 2. Mescla os itens caso ele tenha navegado anonimamente antes de logar
        if (visitorId != null) {
            mesclarCarrinhoAnonimoNoOficial(visitorId, carrinhoOficial);
        }

        return carrinhoOficial;
    }

    private Carrinho processarCarrinhoAnonimo(String visitorId) {
        if (visitorId == null) {
            throw new IllegalArgumentException("Requisição inválida: Nenhum identificador fornecido.");
        }
        return carrinhoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(visitorId,StatusCarrinho.ABERTO)
                .orElseGet(() -> criarCarrinho(visitorId, null));
    }

    private Carrinho criarCarrinho(String visitorId, String usuarioId) {
        Carrinho novo = new Carrinho();
        novo.setVisitorId(visitorId);
        novo.setUsuarioId(usuarioId);
        novo.setStatus(StatusCarrinho.ABERTO);
        return carrinhoRepository.saveAndFlush(novo);
    }

    private void mesclarCarrinhoAnonimoNoOficial(String visitorId, Carrinho carrinhoOficial) {
        carrinhoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(visitorId,StatusCarrinho.ABERTO).ifPresent(anonimo -> {

            // A VALIDAÇÃO DE OURO (Impede o suicídio do objeto no Hibernate)
            if (anonimo.getId().equals(carrinhoOficial.getId())) {
                carrinhoOficial.setVisitorId(null);
                carrinhoRepository.saveAndFlush(carrinhoOficial);
                return;
            }

            // Se forem carrinhos diferentes, fazemos a transferência normal
            transferirItens(anonimo, carrinhoOficial);
            carrinhoRepository.delete(anonimo);
            carrinhoRepository.flush(); // Garante a remoção da chave única do visitante
            carrinhoRepository.saveAndFlush(carrinhoOficial);
        });
    }

    private void transferirItens(Carrinho origem, Carrinho destino) {
        for (ItemCarrinho itemOrigem : origem.getItens()) {
            destino.getItens().stream()
                    .filter(i -> i.getProduto().getId().equals(itemOrigem.getProduto().getId()))
                    .findFirst()
                    .ifPresentOrElse(
                            itemDestino -> itemDestino.setQuantidade(itemDestino.getQuantidade() + itemOrigem.getQuantidade()),
                            () -> adicionarNovoItemAoCarrinho(destino, itemOrigem.getProduto(), itemOrigem.getQuantidade())
                    );
        }
    }

    private void adicionarNovoItemAoCarrinho(Carrinho carrinho, Produto produto, Integer quantidade) {
        ItemCarrinho novoItem = new ItemCarrinho();
        novoItem.setProduto(produto);
        novoItem.setQuantidade(quantidade);
        novoItem.setCarrinho(carrinho);
        carrinho.getItens().add(novoItem);
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS DE AÇÃO
    // ========================================================================

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
    public Carrinho adicionarItem(String visitorId, String usuarioId, Long produtoId, Integer quantidade) {
        Carrinho carrinho = obterOuCriarCarrinho(visitorId, usuarioId);

        Optional<ItemCarrinho> itemExistente = carrinho.getItens().stream()
                .filter(item -> item.getProduto().getId().equals(produtoId))
                .findFirst();

        if (itemExistente.isPresent()) {
            ItemCarrinho item = itemExistente.get();
            int novaQuantidade = item.getQuantidade() + quantidade;
            if (novaQuantidade <= 0) {
                carrinho.getItens().remove(item);
            } else {
                item.setQuantidade(novaQuantidade);
            }
        } else if (quantidade > 0) {
            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado"));
            adicionarNovoItemAoCarrinho(carrinho, produto, quantidade);
        }

        return carrinhoRepository.saveAndFlush(carrinho);
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
    public Carrinho removerItem(String visitorId, String usuarioId, Long produtoId) {
        Carrinho carrinho = obterOuCriarCarrinho(visitorId, usuarioId);
        carrinho.getItens().removeIf(item -> item.getProduto().getId().equals(produtoId));
        return carrinhoRepository.saveAndFlush(carrinho);
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
    public void limparCarrinho(String visitorId, String usuarioId) {
        Carrinho carrinho = obterOuCriarCarrinho(visitorId, usuarioId);
        carrinho.getItens().clear();
        carrinhoRepository.saveAndFlush(carrinho);
    }
}