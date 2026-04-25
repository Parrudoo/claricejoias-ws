package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.ClienteResponseDTO;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.model.HistoricoCobranca;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.repository.HistoricoCobrancaRepository;
import br.com.claricejoias_ws.repository.VendaRepository; // Se necessário, ou buscar das vendas do cliente
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final HistoricoCobrancaRepository historicoCobrancaRepository;

    // Lista todos os clientes
    public List<ClienteResponseDTO> listarTodos() {
        return clienteRepository.findAll().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    // Lista apenas clientes que estão a dever
    public List<ClienteResponseDTO> listarPendentes() {
        return clienteRepository.findClientesInadimplentes().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    // Regista o clique no botão do WhatsApp
    @Transactional
    public void registrarCobranca(Long clienteId, String funcionario) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));

        HistoricoCobranca historico = new HistoricoCobranca();
        historico.setCliente(cliente);
        historico.setFuncionario(funcionario);
        historico.setDataHora(LocalDateTime.now());

        historicoCobrancaRepository.save(historico);
    }

    // Função auxiliar para montar os dados para o Frontend
    private ClienteResponseDTO converterParaDTO(Cliente cliente) {
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setId(cliente.getId());
        dto.setNome(cliente.getNome());
        dto.setTelefone(cliente.getTelefone());

        // Para calcular o total devido, precisamos verificar as vendas associadas ao cliente.
        // Assumindo que na sua entidade Cliente você tem uma List<Venda> vendas;
        // Se não tiver, pode fazer uma query no VendaRepository.
        double totalDevido = 0.0;
        if (cliente.getVendas() != null) {
            totalDevido = cliente.getVendas().stream()
                    .filter(v -> v.getValorDevido() != null)
                    .mapToDouble(v -> v.getValorDevido())
                    .sum();
        }
        dto.setValorDevido(totalDevido);

        // Busca a última cobrança
        historicoCobrancaRepository.findFirstByClienteIdOrderByDataHoraDesc(cliente.getId())
                .ifPresent(historico -> {
                    ClienteResponseDTO.UltimaCobrancaDTO cobrancaDTO = new ClienteResponseDTO.UltimaCobrancaDTO();
                    cobrancaDTO.setDataHora(historico.getDataHora());
                    cobrancaDTO.setFuncionario(historico.getFuncionario());
                    dto.setUltimaCobranca(cobrancaDTO);
                });

        return dto;
    }
}