package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.CarrinhoDTO;
import br.com.claricejoias_ws.dto.ItemCarrinhoDTO;
import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.enums.OrigemPedido;
import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.model.ItemPedido;
import br.com.claricejoias_ws.model.Pedido;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.repository.PedidoRepository;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.StaleObjectStateException;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CarrinhoService {

    // 👇 Agora usamos o PedidoRepository!
    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ModelMapper modelMapper;

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
    public Pedido obterOuCriarCarrinho(String visitorId, String usuarioId) {
        if (isUsuarioLogado(usuarioId)) {
            return processarCarrinhoDeUsuario(visitorId, usuarioId);
        }
        return processarCarrinhoAnonimo(visitorId);
    }

    @Transactional(readOnly = true)
    public CarrinhoDTO consultarCarrinhoAtual(String visitorId, String usuarioId) {
        Optional<Pedido> carrinhoOpt = Optional.empty();

        if (isUsuarioLogado(usuarioId)) {
            carrinhoOpt = pedidoRepository.findFirstByUsuarioIdAndStatusOrderByIdDesc(usuarioId, StatusPedido.CARRINHO);
        } else if (visitorId != null) {
            carrinhoOpt = pedidoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(visitorId, StatusPedido.CARRINHO);
        }

        return carrinhoOpt.map(this::convertToDTO).orElse(null);
    }

    public CarrinhoDTO convertToDTO(Pedido pedido) {
        CarrinhoDTO dto = new CarrinhoDTO();
        dto.setId(pedido.getId());
        dto.setVisitorId(pedido.getVisitorId());
        dto.setUsuarioId(pedido.getUsuarioId());

        List<ItemCarrinhoDTO> itensDTO = pedido.getItens().stream().map(item -> {
            ItemCarrinhoDTO itemDto = new ItemCarrinhoDTO();
            itemDto.setProduto(modelMapper.map(item.getProduto(), ProdutoDTO.class));
            itemDto.setQuantidade(item.getQuantidade());
            return itemDto;
        }).toList();

        dto.setItens(itensDTO);

        BigDecimal total = itensDTO.stream()
                .map(i -> i.getProduto().getPreco().multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setValorTotal(total);
        return dto;
    }

    private boolean isUsuarioLogado(String usuarioId) {
        return usuarioId != null && !usuarioId.trim().isEmpty();
    }

    private Pedido processarCarrinhoDeUsuario(String visitorId, String usuarioId) {
        Pedido carrinhoOficial = pedidoRepository.findFirstByUsuarioIdAndStatusOrderByIdDesc(usuarioId, StatusPedido.CARRINHO)
                .orElseGet(() -> criarCarrinho(null, usuarioId));

        if (visitorId != null) {
            mesclarCarrinhoAnonimoNoOficial(visitorId, carrinhoOficial);
        }

        return carrinhoOficial;
    }

    private Pedido processarCarrinhoAnonimo(String visitorId) {
        if (visitorId == null) {
            throw new IllegalArgumentException("Requisição inválida: Nenhum identificador fornecido.");
        }
        return pedidoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(visitorId, StatusPedido.CARRINHO)
                .orElseGet(() -> criarCarrinho(visitorId, null));
    }

    private Pedido criarCarrinho(String visitorId, String usuarioId) {
        Pedido novo = new Pedido();
        novo.setVisitorId(visitorId);
        novo.setUsuarioId(usuarioId);
        novo.setStatus(StatusPedido.CARRINHO); // Define como carrinho
        novo.setOrigem(OrigemPedido.ECOMMERCE); // Define a origem
        novo.setDataCriacao(LocalDateTime.now());
        // Obs: Não definimos metodo_pagamento ainda, pois é só um carrinho
        // Dependendo de como você configurou o banco, se 'metodo_pagamento' for NOT NULL,
        // coloque um valor padrão temporário como "PENDENTE" aqui.
        novo.setMetodoPagamento("PENDENTE");
        return pedidoRepository.saveAndFlush(novo);
    }

    private void mesclarCarrinhoAnonimoNoOficial(String visitorId, Pedido carrinhoOficial) {
        pedidoRepository.findFirstByVisitorIdAndStatusOrderByIdDesc(visitorId, StatusPedido.CARRINHO).ifPresent(anonimo -> {

            if (anonimo.getId().equals(carrinhoOficial.getId())) {
                carrinhoOficial.setVisitorId(null);
                pedidoRepository.saveAndFlush(carrinhoOficial);
                return;
            }

            transferirItens(anonimo, carrinhoOficial);
            pedidoRepository.delete(anonimo);
            pedidoRepository.flush();
            pedidoRepository.saveAndFlush(carrinhoOficial);
        });
    }

    private void transferirItens(Pedido origem, Pedido destino) {
        for (ItemPedido itemOrigem : origem.getItens()) {
            destino.getItens().stream()
                    .filter(i -> i.getProduto().getId().equals(itemOrigem.getProduto().getId()))
                    .findFirst()
                    .ifPresentOrElse(
                            itemDestino -> itemDestino.setQuantidade(itemDestino.getQuantidade() + itemOrigem.getQuantidade()),
                            () -> adicionarNovoItemAoCarrinho(destino, itemOrigem.getProduto(), itemOrigem.getQuantidade())
                    );
        }
    }

    private void adicionarNovoItemAoCarrinho(Pedido pedido, Produto produto, Integer quantidade) {
        ItemPedido novoItem = new ItemPedido();
        novoItem.setProduto(produto);
        novoItem.setQuantidade(quantidade);
        // Trava o preço atual da joia no momento que vai pro carrinho
        novoItem.setPrecoUnitario(produto.getPreco() != null ? produto.getPreco() : BigDecimal.ZERO);
        novoItem.setSubtotal(novoItem.getPrecoUnitario().multiply(BigDecimal.valueOf(quantidade)));

        // Usa o método utilitário que corrigimos na entidade Pedido
        pedido.addItem(novoItem);
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
    public Pedido adicionarItem(String visitorId, String usuarioId, Long produtoId, Integer quantidade) {
        Pedido carrinho = obterOuCriarCarrinho(visitorId, usuarioId);

        Optional<ItemPedido> itemExistente = carrinho.getItens().stream()
                .filter(item -> item.getProduto().getId().equals(produtoId))
                .findFirst();

        if (itemExistente.isPresent()) {
            ItemPedido item = itemExistente.get();
            int novaQuantidade = item.getQuantidade() + quantidade;
            if (novaQuantidade <= 0) {
                carrinho.getItens().remove(item);
            } else {
                item.setQuantidade(novaQuantidade);
                item.setSubtotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(novaQuantidade)));
            }
        } else if (quantidade > 0) {
            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado"));
            adicionarNovoItemAoCarrinho(carrinho, produto, quantidade);
        }

        return pedidoRepository.saveAndFlush(carrinho);
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
    public Pedido removerItem(String visitorId, String usuarioId, Long produtoId) {
        Pedido carrinho = obterOuCriarCarrinho(visitorId, usuarioId);
        carrinho.getItens().removeIf(item -> item.getProduto().getId().equals(produtoId));
        return pedidoRepository.saveAndFlush(carrinho);
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
        Pedido carrinho = obterOuCriarCarrinho(visitorId, usuarioId);
        carrinho.getItens().clear();
        pedidoRepository.saveAndFlush(carrinho);
    }
}