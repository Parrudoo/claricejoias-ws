package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.CheckoutDTO;
import br.com.claricejoias_ws.dto.PedidoDTO;
import br.com.claricejoias_ws.dto.PedidoRequestDTO;
import br.com.claricejoias_ws.enums.OrigemPedido;
import br.com.claricejoias_ws.enums.StatusParcela;
import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.model.*;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import br.com.claricejoias_ws.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public Pedido registrarPedidoPDV(PedidoRequestDTO dto, String loginOperador) {
        Pedido pedido = new Pedido();
        pedido.setDataCriacao(LocalDateTime.now());
        pedido.setLoginOperador(loginOperador);

        // Define a origem e status para venda direta na loja
        pedido.setOrigem(OrigemPedido.PDV);
        pedido.setStatus(StatusPedido.PAGO); // Se for fiado, você pode mudar para PENDENTE_PAGAMENTO se preferir

        // Garante que o total não seja nulo
        BigDecimal totalVenda = dto.getTotal() != null ? dto.getTotal() : BigDecimal.ZERO;
        pedido.setTotal(totalVenda);

        String metodo = dto.getPagamento().getMetodo();
        BigDecimal valorEntradaInput = dto.getPagamento().getValorEntrada();
        BigDecimal valorRecebidoInput = dto.getPagamento().getValorRecebido();
        Integer parcelasInput = dto.getPagamento().getParcelas();

        pedido.setMetodoPagamento(metodo);
        pedido.setParcelas(parcelasInput != null && parcelasInput > 0 ? parcelasInput : 1);

        BigDecimal valorEntradaSeguro = (valorEntradaInput != null) ? valorEntradaInput : BigDecimal.ZERO;
        pedido.setValorEntrada(valorEntradaSeguro);

        // Lógica de Troco (Apenas para espécie)
        if ("especie".equalsIgnoreCase(metodo) && valorRecebidoInput != null) {
            pedido.setValorRecebido(valorRecebidoInput);
            pedido.setTroco(valorRecebidoInput.subtract(totalVenda).max(BigDecimal.ZERO));
        } else {
            pedido.setValorRecebido(totalVenda);
            pedido.setTroco(BigDecimal.ZERO);
        }

        // Lógica de Entrada e Saldo Devedor (Fiado)
        if ("fiado".equalsIgnoreCase(metodo)) {
            BigDecimal saldoDevedor = totalVenda.subtract(valorEntradaSeguro);
            pedido.setValorDevido(saldoDevedor);

            int qtdParcelas = pedido.getParcelas();
            // O SEGREDO DA DIVISÃO COM BIGDECIMAL:
            BigDecimal valorPorParcela = saldoDevedor.divide(
                    BigDecimal.valueOf(qtdParcelas), 2, RoundingMode.HALF_UP
            );

            List<Parcela> listaParcelas = new ArrayList<>();
            LocalDate dataAtual = LocalDate.now();

            for (int i = 1; i <= qtdParcelas; i++) {
                Parcela parcela = new Parcela();
                parcela.setPedido(pedido);
                parcela.setNumeroParcela(i);
                parcela.setValor(valorPorParcela);
                parcela.setStatus(StatusParcela.PENDENTE);
                parcela.setDataVencimento(dataAtual.plusMonths(i));
                listaParcelas.add(parcela);
            }
            pedido.setParcelasDetalhadas(listaParcelas);
        } else {
            pedido.setValorDevido(BigDecimal.ZERO);
        }

        // Mapeamento de Itens e BAIXA DE ESTOQUE
        List<ItemPedido> itens = dto.getItens().stream().map(itemDto -> {
            Produto produto = produtoRepository.findById(itemDto.getId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

            // ---> AQUI ENTRA A REGRA DE ESTOQUE <---
            // Tenta diminuir o estoque. Se a quantidade vendida for maior que o saldo,
            // o método lançará a exception e o @Transactional fará o rollback de todo o PDV.
            produto.diminuirEstoque(itemDto.getQuantidade());

            ItemPedido item = new ItemPedido();
            item.setPedido(pedido);
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());

            BigDecimal precoUnitario = itemDto.getPreco() != null ? itemDto.getPreco() : BigDecimal.ZERO;
            item.setPrecoUnitario(precoUnitario);
            item.setSubtotal(precoUnitario.multiply(BigDecimal.valueOf(itemDto.getQuantidade())));

            return item;
        }).collect(Collectors.toList());

        pedido.setItens(itens);

        // Lógica do Cliente (Busca ou Cria novo)
        if (dto.getCliente() != null) {
            Cliente cliente = clienteRepository.findByWhatsapp(dto.getCliente().getTelefone())
                    .orElseGet(() -> {
                        Cliente novo = new Cliente();
                        novo.setNome(dto.getCliente().getNome());
                        novo.setWhatsapp(dto.getCliente().getTelefone());
                        novo.setUsuarioId(java.util.UUID.randomUUID().toString());
                        return clienteRepository.save(novo);
                    });
            pedido.setCliente(cliente);

            // Adiciona na dívida geral do cliente se for fiado
            if ("fiado".equalsIgnoreCase(metodo)) {
                BigDecimal dividaAtual = cliente.getSaldoDevedor() != null ? cliente.getSaldoDevedor() : BigDecimal.ZERO;
                cliente.setSaldoDevedor(dividaAtual.add(pedido.getValorDevido()));
            }
        }

        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Pedido realizarCheckoutOnline(String visitorId, String usuarioId, CheckoutDTO dto) {

        // 1. O carrinho agora é apenas um Pedido que estava aguardando (Status = CARRINHO)
        Pedido carrinhoAtual = pedidoRepository.buscarCarrinhoAtivo(visitorId, usuarioId)
                .orElseThrow(() -> new RuntimeException("Nenhum carrinho ativo encontrado para checkout."));

        if (carrinhoAtual.getItens().isEmpty()) {
            throw new RuntimeException("Não é possível finalizar um pedido com a maleta vazia.");
        }

        // 2. BAIXA DE ESTOQUE (Regra de Negócio)
        // Passa por todos os itens do carrinho e deduz o estoque antes de finalizar a venda.
        for (ItemPedido item : carrinhoAtual.getItens()) {
            Produto produto = item.getProduto();

            // Se não houver saldo, o método diminuirEstoque lançará uma exception,
            // e o @Transactional cancelará todo o processo imediatamente.
            produto.diminuirEstoque(item.getQuantidade());

            // Dica: Se quiser garantir que o preço não mudou desde que o cliente botou no carrinho:
            // item.setPrecoUnitario(produto.getPreco());
        }

        // 3. Busca ou cria o Cliente
        Cliente cliente = clienteRepository.findByWhatsapp(dto.getWhatsapp())
                .orElseGet(() -> {
                    Cliente novoCliente = new Cliente();
                    novoCliente.setNome(dto.getNome());
                    novoCliente.setWhatsapp(dto.getWhatsapp());
                    novoCliente.setEmail(dto.getEmail());
                    return clienteRepository.save(novoCliente);
                });

        // 4. Atualiza os dados do Pedido que já existe
        carrinhoAtual.setCliente(cliente);
        carrinhoAtual.setDataAtualizacao(LocalDateTime.now());

        // Altera o status e configura o financeiro do checkout
        carrinhoAtual.setStatus(StatusPedido.PENDENTE_PAGAMENTO);
        carrinhoAtual.setMetodoPagamento(dto.getMetodoPagamento());
        carrinhoAtual.setParcelas(dto.getParcelas());
        carrinhoAtual.setValorRecebido(dto.getValorRecebido());
        carrinhoAtual.setValorEntrada(dto.getValorEntrada());

        // Como os produtos foram modificados (estoque baixou), ao salvar o pedido o JPA/Hibernate
        // fará o update automático nas tabelas de Produto também, sem precisarmos chamar produtoRepository.save()

        return pedidoRepository.save(carrinhoAtual);
    }

    public Page<PedidoDTO> listarPedidos(String loginOperador, String metodoPagamento, LocalDate dataInicio, LocalDate dataFim, Pageable pageable) {
        LocalDateTime inicioDia = (dataInicio != null) ? dataInicio.atStartOfDay() : null;
        LocalDateTime fimDia = (dataFim != null) ? dataFim.atTime(LocalTime.MAX) : null;

        // Aqui você pode adicionar um filtro para listar apenas status PAGO, ENVIADO, etc (ignorando CARRINHO)
        Page<Pedido> pedidosPage = pedidoRepository.findComFiltros(loginOperador, metodoPagamento, inicioDia, fimDia, pageable);

        return pedidosPage.map(p -> modelMapper.map(p, PedidoDTO.class));
    }

    public Page<PedidoDTO> listarMeusPedidos(String usuarioId, Pageable pageable) {
        Page<Pedido> pedidos = pedidoRepository.findByUsuarioId(usuarioId, pageable);
        return pedidos.map(pedido -> modelMapper.map(pedido, PedidoDTO.class));
    }
}