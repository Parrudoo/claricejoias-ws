package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.model.Carrinho;
import br.com.claricejoias_ws.model.ItemCarrinho;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.repository.CarrinhoRepository;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
public class CarrinhoService {

    @Autowired private CarrinhoRepository carrinhoRepository;
    @Autowired private ProdutoRepository produtoRepository;

    @Transactional
    public Carrinho obterOuCriarCarrinho(String visitorId, String usuarioId) {
        // 1. Prioridade: Usuário Logado
        if (usuarioId != null && !usuarioId.isEmpty()) {
            Carrinho carrinhoOficial = carrinhoRepository.findByUsuarioId(usuarioId)
                    .orElseGet(() -> {
                        Carrinho novo = new Carrinho();
                        novo.setUsuarioId(usuarioId);
                        return carrinhoRepository.saveAndFlush(novo);
                    });

            // Verifica se existe um carrinho anônimo para fundir
            if (visitorId != null) {
                carrinhoRepository.findByVisitorId(visitorId).ifPresent(anonimo -> {
                    // Em vez de mover o objeto (que tem o ID antigo),
                    // apenas pegamos a lógica de negócio (Produto e Quantidade)
                    for (ItemCarrinho itemAnon : anonimo.getItens()) {

                        carrinhoOficial.getItens().stream()
                                .filter(i -> i.getProduto().getId().equals(itemAnon.getProduto().getId()))
                                .findFirst()
                                .ifPresentOrElse(
                                        // Se já tem o produto no oficial, apenas soma a quantidade
                                        itemOficial -> itemOficial.setQuantidade(itemOficial.getQuantidade() + itemAnon.getQuantidade()),
                                        // Se não tem, cria um NOVO item vinculado ao oficial
                                        () -> {
                                            ItemCarrinho novoItem = new ItemCarrinho();
                                            novoItem.setProduto(itemAnon.getProduto());
                                            novoItem.setQuantidade(itemAnon.getQuantidade());
                                            novoItem.setCarrinho(carrinhoOficial);
                                            carrinhoOficial.getItens().add(novoItem);
                                        }
                                );
                    }

                    // IMPORTANTE: Primeiro limpamos os itens do anônimo para evitar o erro de Constraint
                    anonimo.getItens().clear();
                    carrinhoRepository.delete(anonimo);
                    carrinhoRepository.flush(); // Garante que o anônimo morreu antes de seguir
                });
            }

            return carrinhoRepository.saveAndFlush(carrinhoOficial);
        }

        // 2. Fluxo Anônimo
        return carrinhoRepository.findByVisitorId(visitorId)
                .orElseGet(() -> {
                    Carrinho novo = new Carrinho();
                    novo.setVisitorId(visitorId);
                    return carrinhoRepository.saveAndFlush(novo);
                });
    }

    @Transactional
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
            ItemCarrinho novoItem = new ItemCarrinho();
            novoItem.setProduto(produto);
            novoItem.setQuantidade(quantidade);
            novoItem.setCarrinho(carrinho);
            carrinho.getItens().add(novoItem);
        }

        return carrinhoRepository.saveAndFlush(carrinho);
    }

    @Transactional
    public Carrinho removerItem(String visitorId, String usuarioId, Long produtoId) {
        Carrinho carrinho = obterOuCriarCarrinho(visitorId, usuarioId);
        carrinho.getItens().removeIf(item -> item.getProduto().getId().equals(produtoId));
        return carrinhoRepository.save(carrinho);
    }

    @Transactional
    public void limparCarrinho(String visitorId, String usuarioId) {
        Carrinho carrinho = obterOuCriarCarrinho(visitorId, usuarioId);
        carrinho.getItens().clear();
        carrinhoRepository.save(carrinho);
    }
}