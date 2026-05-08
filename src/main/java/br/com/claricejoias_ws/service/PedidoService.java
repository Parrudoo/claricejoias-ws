package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.ItemPedidoDTO;
import br.com.claricejoias_ws.dto.PedidoDTO;
import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.model.Pedido;
import br.com.claricejoias_ws.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ModelMapper modelMapper;

    @Transactional(readOnly = true)
    public List<PedidoDTO> buscarMeusPedidos(String visitorId, String usuarioId) {
        List<Pedido> pedidos = new ArrayList<>();

        // 1. Prioriza buscar pelo Usuário Logado
        if (usuarioId != null && !usuarioId.trim().isEmpty()) {
            pedidos = pedidoRepository.findByUsuarioIdOrderByIdDesc(usuarioId);
        }
        // 2. Se não estiver logado, busca pelo Visitante (Cookie/LocalStorage)
        else if (visitorId != null && !visitorId.trim().isEmpty()) {
            pedidos = pedidoRepository.findByVisitorIdOrderByIdDesc(visitorId);
        }

        // 3. Converte a Entidade para DTO
        return pedidos.stream().map(this::converterParaDTO).toList();
    }

    // 1. Método para buscar por ID
    @Transactional(readOnly = true)
    public PedidoDTO buscarPorId(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado."));
        return converterParaDTO(pedido);
    }

    // 2. Método para listar todos (Admin)
    @Transactional(readOnly = true)
    public List<PedidoDTO> listarTodos() {
        // Busca todos e ordena do mais recente pro mais antigo
        List<Pedido> pedidos = pedidoRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        return pedidos.stream().map(this::converterParaDTO).toList();
    }

    // 3. Método para atualizar o status (Admin)
    @Transactional
    public PedidoDTO atualizarStatus(Long id, StatusPedido novoStatus) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado."));

        pedido.setStatusPedido(novoStatus);
        pedido = pedidoRepository.save(pedido);

        return converterParaDTO(pedido);
    }

    private PedidoDTO converterParaDTO(Pedido pedido) {
        PedidoDTO dto = new PedidoDTO();
        dto.setId(pedido.getId());
        dto.setDataCriacao(pedido.getDataCriacao());
        dto.setStatusPedido(pedido.getStatusPedido());
        dto.setFormaPagamento(pedido.getFormaPagamento());
        dto.setTotalCobrado(pedido.getTotalCobrado());

        List<ItemPedidoDTO> itensDTO = pedido.getItens().stream().map(item -> {
            ItemPedidoDTO itemDto = new ItemPedidoDTO();
            itemDto.setProduto(modelMapper.map(item.getProduto(), ProdutoDTO.class));
            itemDto.setQuantidade(item.getQuantidade());
            itemDto.setPrecoUnitario(item.getPrecoUnitario());
            return itemDto;
        }).toList();

        dto.setItens(itensDTO);
        return dto;
    }
}