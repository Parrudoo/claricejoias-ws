package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.CheckoutDTO;
import br.com.claricejoias_ws.dto.VendaDTO;
import br.com.claricejoias_ws.dto.VendaRequestDTO;
import br.com.claricejoias_ws.model.*;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import br.com.claricejoias_ws.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final CarrinhoService carrinhoService;
    private final ModelMapper modelMapper;

    @Transactional
    public Venda registrarVenda(VendaRequestDTO dto, String loginOperador) {
        Venda venda = new Venda();
        venda.setDataVenda(LocalDateTime.now());
        venda.setLoginOperador(loginOperador);

        // Garante que o total não seja nulo
        BigDecimal totalVenda = dto.getTotal() != null ? dto.getTotal() : BigDecimal.ZERO;
        venda.setTotal(totalVenda);

        // Dados base de pagamento vindos do DTO (Certifique-se que no DTO eles também são BigDecimal)
        String metodo = dto.getPagamento().getMetodo();
        BigDecimal valorEntradaInput = dto.getPagamento().getValorEntrada();
        BigDecimal valorRecebidoInput = dto.getPagamento().getValorRecebido();
        Integer parcelasInput = dto.getPagamento().getParcelas();

        venda.setMetodoPagamento(metodo);
        venda.setParcelas(parcelasInput != null && parcelasInput > 0 ? parcelasInput : 1);

        // 1. CORREÇÃO: Garante que o valor da entrada nunca seja nulo
        BigDecimal valorEntradaSeguro = (valorEntradaInput != null) ? valorEntradaInput : BigDecimal.ZERO;
        venda.setValorEntrada(valorEntradaSeguro);

        // Lógica de Troco (Apenas para espécie)
        if ("especie".equalsIgnoreCase(metodo) && valorRecebidoInput != null) {
            venda.setValorRecebido(valorRecebidoInput);
            // Calcula o troco e usa o .max(ZERO) para evitar troco negativo
            venda.setTroco(valorRecebidoInput.subtract(totalVenda).max(BigDecimal.ZERO));
        } else {
            venda.setValorRecebido(totalVenda);
            venda.setTroco(BigDecimal.ZERO);
        }

        // Lógica de Entrada e Saldo Devedor (Fiado)
        if ("fiado".equalsIgnoreCase(metodo)) {
            BigDecimal saldoDevedor = totalVenda.subtract(valorEntradaSeguro);
            venda.setValorDevido(saldoDevedor);

            // GERA AS PARCELAS
            int qtdParcelas = venda.getParcelas();

            //  O SEGREDO DA DIVISÃO COM BIGDECIMAL:
            // Divide pelo número de parcelas, força 2 casas decimais, e arredonda padrão (ex: 33.33)
            BigDecimal valorPorParcela = saldoDevedor.divide(
                    BigDecimal.valueOf(qtdParcelas), 2, RoundingMode.HALF_UP
            );

            List<Parcela> listaParcelas = new ArrayList<>();
            LocalDate dataAtual = LocalDate.now();

            for (int i = 1; i <= qtdParcelas; i++) {
                Parcela parcela = new Parcela();
                parcela.setVenda(venda);
                parcela.setNumeroParcela(i);
                parcela.setValor(valorPorParcela);
                parcela.setStatus("PENDENTE");

                // Joga o vencimento para o último dia do próximo mês
                LocalDate vencimento = dataAtual.plusMonths(i).with(TemporalAdjusters.lastDayOfMonth());
                parcela.setDataVencimento(vencimento);

                listaParcelas.add(parcela);
            }

            venda.setParcelasDetalhadas(listaParcelas);

        } else {
            venda.setValorDevido(BigDecimal.ZERO);
        }

        // Mapeamento de Itens
        List<ItemVenda> itens = dto.getItens().stream().map(itemDto -> {
            Produto produto = produtoRepository.findById(itemDto.getId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

            ItemVenda item = new ItemVenda();
            item.setVenda(venda);
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());

            // Puxa o preço do DTO (Garantindo que é BigDecimal)
            BigDecimal precoUnitario = itemDto.getPreco() != null ? itemDto.getPreco() : BigDecimal.ZERO;
            item.setPrecoUnitario(precoUnitario);

            // Multiplica usando o .multiply() e transformando a quantidade em BigDecimal
            BigDecimal subtotal = precoUnitario.multiply(BigDecimal.valueOf(itemDto.getQuantidade()));
            item.setSubtotal(subtotal);

            return item;
        }).collect(Collectors.toList());

        venda.setItens(itens);

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
            venda.setCliente(cliente);

            // IMPORTANTE: Se o cliente compra fiado, a gente precisa adicionar na dívida geral dele
            if ("fiado".equalsIgnoreCase(metodo)) {
                BigDecimal dividaAtual = cliente.getSaldoDevedor() != null ? cliente.getSaldoDevedor() : BigDecimal.ZERO;
                cliente.setSaldoDevedor(dividaAtual.add(venda.getValorDevido()));
                // Como o cliente vai ser salvo via Cascade ou manualmente depois, o saldo fica amarrado
            }
        }

        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda realizarCheckout(String visitorId, String usuarioId, CheckoutDTO dto) {

        // 1. Busca o carrinho atual no banco (AGORA PASSANDO OS DOIS IDs)
        Carrinho carrinho = carrinhoService.obterOuCriarCarrinho(visitorId, usuarioId);

        if (carrinho.getItens().isEmpty()) {
            throw new RuntimeException("Não é possível finalizar um pedido com a maleta vazia.");
        }

        // 2. Busca o Cliente pelo WhatsApp (ou cria um novo se não existir)
        Cliente cliente = clienteRepository.findByWhatsapp(dto.getWhatsapp())
                .orElseGet(() -> {
                    Cliente novoCliente = new Cliente();
                    novoCliente.setNome(dto.getNome());
                    novoCliente.setWhatsapp(dto.getWhatsapp());
                    novoCliente.setEmail(dto.getEmail());

                    // (Opcional) Se você tiver um campo keycloakId na entidade Cliente,
                    // você pode salvá-lo aqui: novoCliente.setKeycloakId(usuarioId);

                    return clienteRepository.save(novoCliente);
                });

        // 3. Monta a entidade Venda
        Venda venda = new Venda();
        venda.setCliente(cliente);
        venda.setDataVenda(LocalDateTime.now());
        venda.setTotal(carrinho.getValorTotal()); // O BigDecimal que criamos no Carrinho

        // Configurações financeiras vindas do DTO
        venda.setMetodoPagamento(dto.getMetodoPagamento());
        venda.setParcelas(dto.getParcelas());
        venda.setValorRecebido(dto.getValorRecebido());
        venda.setValorEntrada(dto.getValorEntrada());

        // 4. Transfere os itens do Carrinho para ItemVenda
        for (ItemCarrinho itemCart : carrinho.getItens()) {
            ItemVenda itemVenda = new ItemVenda();
            itemVenda.setProduto(itemCart.getProduto());
            itemVenda.setQuantidade(itemCart.getQuantidade());

            // É importante travar o preço no ItemVenda para o preço atual da joia,
            // assim, se o preço mudar amanhã, o histórico de vendas não é afetado!
            itemVenda.setPrecoUnitario(itemCart.getProduto().getPreco());

            itemVenda.setVenda(venda); // Relacionamento bidirecional

            venda.getItens().add(itemVenda);
        }

        // 5. Salva a Venda (graças ao CascadeType.ALL e ao @PrePersist,
        // os itens serão salvos e o valorDevido será calculado sozinho)
        Venda vendaSalva = vendaRepository.save(venda);

        // 6. MÁGICA: Limpa o carrinho! (AGORA PASSANDO OS DOIS IDs)
        carrinhoService.limparCarrinho(visitorId, usuarioId);

        return vendaSalva;
    }


    public Page<VendaDTO> listarVendas(String loginOperador, String metodoPagamento, LocalDate dataInicio, LocalDate dataFim, Pageable pageable) {

        // Converte a data inicial para o começo do dia (00:00:00)
        LocalDateTime inicioDia = (dataInicio != null) ? dataInicio.atStartOfDay() : null;

        // Converte a data final para o fim do dia (23:59:59)
        LocalDateTime fimDia = (dataFim != null) ? dataFim.atTime(LocalTime.MAX) : null;

        Page<Venda> vendasPage = vendaRepository.findComFiltros(loginOperador, metodoPagamento, inicioDia, fimDia, pageable);

        return vendasPage.map(v -> modelMapper.map(v, VendaDTO.class));
    }
}