package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.VendaRequestDTO;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.model.ItemVenda;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.model.Venda;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import br.com.claricejoias_ws.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // Dados de Pagamento
        venda.setMetodoPagamento(dto.getPagamento().getMetodo());
        venda.setParcelas(dto.getPagamento().getParcelas());
        venda.setValorRecebido(dto.getPagamento().getValorRecebido());

        // Calcula o troco se for em espécie
        if ("especie".equalsIgnoreCase(dto.getPagamento().getMetodo()) && dto.getPagamento().getValorRecebido() != null) {
            venda.setTroco(dto.getPagamento().getValorRecebido() - dto.getTotal());
        } else {
            venda.setTroco(0.0);
        }

        // Mapeia os Itens da Venda
        List<ItemVenda> itens = dto.getItens().stream().map(itemDto -> {
            // Busca o produto real no banco de dados
            Produto produto = produtoRepository.findById(itemDto.getId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado com ID: " + itemDto.getId()));

            ItemVenda item = new ItemVenda();
            item.setVenda(venda); // Vincula o item à venda principal
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitario(itemDto.getPreco());
            item.setSubtotal(itemDto.getPreco() * itemDto.getQuantidade());

            return item;
        }).collect(Collectors.toList());

        venda.setItens(itens);

        // Lógica do Cliente
        if (dto.getCliente() != null) {
            // Busca cliente existente pelo telefone ou cria um novo
            Cliente cliente = clienteRepository.findByTelefone(dto.getCliente().getTelefone())
                    .orElseGet(() -> {
                        Cliente novo = new Cliente();
                        novo.setNome(dto.getCliente().getNome());
                        novo.setTelefone(dto.getCliente().getTelefone());
                        return clienteRepository.save(novo);
                    });
            venda.setCliente(cliente);
        }

        // Lógica de Entrada e Fiado
        venda.setValorEntrada(dto.getPagamento().getValorRecebido() != null ? dto.getPagamento().getValorRecebido() : 0.0);

        if ("fiado".equalsIgnoreCase(dto.getPagamento().getMetodo())) {
            venda.setValorDevido(venda.getTotal() - venda.getValorEntrada());
        } else {
            venda.setValorDevido(0.0);
        }

        return vendaRepository.save(venda);
    }


    public List<Venda> listarVendas() {
        return vendaRepository.findAll();
    }
}