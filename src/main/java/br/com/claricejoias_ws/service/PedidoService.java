package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.CheckoutDTO;
import br.com.claricejoias_ws.dto.PedidoDTO;
import br.com.claricejoias_ws.dto.PedidoRequestDTO;
import br.com.claricejoias_ws.enums.OrigemPedido;
import br.com.claricejoias_ws.enums.StatusParcela;
import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.model.*;
import br.com.claricejoias_ws.repository.*;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final RevendedorRepository revendedorRepository;
    private final EstoqueRevendedorRepository estoqueRevendedorRepository;
    private final ModelMapper modelMapper;


    @Transactional
    public Pedido registrarPedidoPDV(PedidoRequestDTO dto, String userId, boolean isAdmin, String loginOperador) {
        Pedido pedido = new Pedido();
        pedido.setDataCriacao(LocalDateTime.now());
        pedido.setLoginOperador(loginOperador);
        pedido.setOrigem(OrigemPedido.PDV);
        pedido.setStatus(StatusPedido.PAGO);

        // 1. Identifica se existe um revendedor vinculado
        Revendedor revendedor = null;
        if (!isAdmin) {
            revendedor = revendedorRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Revendedor não cadastrado ou não autorizado."));
            pedido.setRevendedor(revendedor); // Vincula o pedido ao revendedor
        }

        // --- Lógica de Financeiro (Total, Pagamento, Troco) ---
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

        if ("especie".equalsIgnoreCase(metodo) && valorRecebidoInput != null) {
            pedido.setValorRecebido(valorRecebidoInput);
            pedido.setTroco(valorRecebidoInput.subtract(totalVenda).max(BigDecimal.ZERO));
        } else {
            pedido.setValorRecebido(totalVenda);
            pedido.setTroco(BigDecimal.ZERO);
        }

        // Lógica de Parcelas para Fiado
        if ("fiado".equalsIgnoreCase(metodo)) {
            BigDecimal saldoDevedor = totalVenda.subtract(valorEntradaSeguro);
            pedido.setValorDevido(saldoDevedor);

            int qtdParcelas = pedido.getParcelas();
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

        // 2. Mapeamento de Itens e BAIXA DE ESTOQUE (Central vs Maleta)
        final Revendedor revendedorFinal = revendedor; // Necessário para o lambda
        List<ItemPedido> itens = dto.getItens().stream().map(itemDto -> {
            Produto produto = produtoRepository.findById(itemDto.getId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

            if (isAdmin) {
                // ---> REGRA ADMIN: Baixa do Estoque Central <---
                produto.diminuirEstoqueCentral(itemDto.getQuantidade());
            } else {
                // ---> REGRA REVENDEDOR: Baixa da Maleta <---
                EstoqueRevendedor estoqueMaleta = estoqueRevendedorRepository
                        .findByProdutoIdAndRevendedorId(produto.getId(), revendedorFinal.getId())
                        .orElseThrow(() -> new RuntimeException("Produto não consta na sua maleta."));

                estoqueMaleta.diminuirEstoque(itemDto.getQuantidade());
            }

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

        // 3. Lógica do Cliente (Isolado para Revendedor / Global para Admin)
        if (dto.getCliente() != null) {
            String whatsapp = dto.getCliente().getTelefone();

            Optional<Cliente> clienteOpt;
            if (isAdmin) {
                // Admin busca na base global (onde revendedor_id é nulo)
                clienteOpt = clienteRepository.findByWhatsapp(whatsapp);
            } else {
                // Revendedor busca na sua base isolada
                clienteOpt = clienteRepository.findByWhatsappAndRevendedorId(whatsapp, revendedor.getId());
            }

            Cliente cliente = clienteOpt.orElseGet(() -> {
                Cliente novo = new Cliente();
                novo.setNome(dto.getCliente().getNome());
                novo.setWhatsapp(whatsapp);
                novo.setRevendedor(revendedorFinal); // Se for admin, fica nulo
                novo.setUsuarioId(java.util.UUID.randomUUID().toString());
                return clienteRepository.save(novo);
            });

            pedido.setCliente(cliente);

            if ("fiado".equalsIgnoreCase(metodo)) {
                BigDecimal dividaAtual = cliente.getSaldoDevedor() != null ? cliente.getSaldoDevedor() : BigDecimal.ZERO;
                cliente.setSaldoDevedor(dividaAtual.add(pedido.getValorDevido()));
            }
        }

        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Pedido realizarCheckoutOnline(String visitorId, String loginOperador, CheckoutDTO dto) {

        // Identifica o revendedor dono do catálogo virtual
        Revendedor revendedor = revendedorRepository.findById(loginOperador)
                .orElseThrow(() -> new RuntimeException("Revendedor não cadastrado."));

        Pedido carrinhoAtual = pedidoRepository.buscarCarrinhoAtivo(visitorId, loginOperador)
                .orElseThrow(() -> new RuntimeException("Nenhum carrinho ativo encontrado para checkout."));

        if (carrinhoAtual.getItens().isEmpty()) {
            throw new RuntimeException("Não é possível finalizar um pedido com a maleta vazia.");
        }

        // BAIXA DE ESTOQUE DA MALETA
        for (ItemPedido item : carrinhoAtual.getItens()) {
            EstoqueRevendedor estoqueMaleta = estoqueRevendedorRepository
                    .findByProdutoIdAndRevendedorId(item.getProduto().getId(), revendedor.getId())
                    .orElseThrow(() -> new RuntimeException("Produto indisponível na maleta do revendedor."));

            estoqueMaleta.diminuirEstoque(item.getQuantidade());
        }

        // Busca ou cria o Cliente isolado para o revendedor
        Cliente cliente = clienteRepository.findByWhatsappAndRevendedorId(dto.getWhatsapp(), revendedor.getId())
                .orElseGet(() -> {
                    Cliente novoCliente = new Cliente();
                    novoCliente.setNome(dto.getNome());
                    novoCliente.setWhatsapp(dto.getWhatsapp());
                    novoCliente.setEmail(dto.getEmail());
                    novoCliente.setRevendedor(revendedor); // Isolamento
                    return clienteRepository.save(novoCliente);
                });

        carrinhoAtual.setCliente(cliente);
        carrinhoAtual.setDataAtualizacao(LocalDateTime.now());
        carrinhoAtual.setRevendedor(revendedor);

        carrinhoAtual.setStatus(StatusPedido.PENDENTE_PAGAMENTO);
        carrinhoAtual.setMetodoPagamento(dto.getMetodoPagamento());
        carrinhoAtual.setParcelas(dto.getParcelas());
        carrinhoAtual.setValorRecebido(dto.getValorRecebido());
        carrinhoAtual.setValorEntrada(dto.getValorEntrada());

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