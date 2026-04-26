package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.VendaRequestDTO;
import br.com.claricejoias_ws.model.*;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import br.com.claricejoias_ws.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
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

    @Transactional
    public Venda registrarVenda(VendaRequestDTO dto) {
        Venda venda = new Venda();
        venda.setDataVenda(LocalDateTime.now());
        venda.setTotal(dto.getTotal());

        // Dados base de pagamento vindos do DTO
        String metodo = dto.getPagamento().getMetodo();
        Double valorEntradaInput = dto.getPagamento().getValorEntrada();
        Double valorRecebidoInput = dto.getPagamento().getValorRecebido();
        Integer parcelasInput = dto.getPagamento().getParcelas();

        venda.setMetodoPagamento(metodo);
        venda.setParcelas(parcelasInput != null ? parcelasInput : 1);

        // 1. CORREÇÃO: Garante que o valor da entrada nunca seja nulo e seta na venda
        double valorEntradaSeguro = (valorEntradaInput != null) ? valorEntradaInput : 0.0;
        venda.setValorEntrada(valorEntradaSeguro);

        // Lógica de Troco (Apenas para espécie)
        if ("especie".equalsIgnoreCase(metodo) && valorRecebidoInput != null) {
            venda.setValorRecebido(valorRecebidoInput);
            venda.setTroco(Math.max(0.0, valorRecebidoInput - dto.getTotal()));
        } else {
            venda.setValorRecebido(dto.getTotal());
            venda.setTroco(0.0);
        }

        // Lógica de Entrada e Saldo Devedor (Fiado)
        if ("fiado".equalsIgnoreCase(metodo)) {
            // Agora o getValorEntrada() tem um número garantido, então não dará NullPointerException
            Double saldoDevedor = venda.getTotal() - venda.getValorEntrada();
            venda.setValorDevido(saldoDevedor);

            // GERA AS PARCELAS
            int qtdParcelas = venda.getParcelas();
            double valorPorParcela = saldoDevedor / qtdParcelas;
            List<Parcela> listaParcelas = new ArrayList<>();

            LocalDate dataAtual = LocalDate.now();

            for (int i = 1; i <= qtdParcelas; i++) {
                Parcela parcela = new Parcela();
                parcela.setVenda(venda);
                parcela.setNumeroParcela(i);
                parcela.setValor(valorPorParcela);
                parcela.setStatus("PENDENTE");

                // Joga o vencimento para o último dia do próximo mês, depois do outro, etc.
                LocalDate vencimento = dataAtual.plusMonths(i).with(TemporalAdjusters.lastDayOfMonth());
                parcela.setDataVencimento(vencimento);

                listaParcelas.add(parcela);
            }
            venda.setParcelasDetalhadas(listaParcelas);

        } else {
            venda.setValorDevido(0.0);
        }

        // Mapeamento de Itens
        List<ItemVenda> itens = dto.getItens().stream().map(itemDto -> {
            Produto produto = produtoRepository.findById(itemDto.getId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

            ItemVenda item = new ItemVenda();
            item.setVenda(venda);
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitario(itemDto.getPreco());
            item.setSubtotal(itemDto.getPreco() * itemDto.getQuantidade());
            return item;
        }).collect(Collectors.toList());

        venda.setItens(itens);

        // Lógica do Cliente (Busca ou Cria novo)
        if (dto.getCliente() != null) {
            Cliente cliente = clienteRepository.findByTelefone(dto.getCliente().getTelefone())
                    .orElseGet(() -> {
                        Cliente novo = new Cliente();
                        novo.setNome(dto.getCliente().getNome());
                        novo.setTelefone(dto.getCliente().getTelefone());
                        return clienteRepository.save(novo);
                    });
            venda.setCliente(cliente);
        }

        return vendaRepository.save(venda);
    }


    public List<Venda> listarVendas() {
        return vendaRepository.findAll();
    }
}