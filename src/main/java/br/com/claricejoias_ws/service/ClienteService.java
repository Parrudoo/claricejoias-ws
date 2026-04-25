package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.ClienteResponseDTO;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.model.HistoricoCobranca;
import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.model.Venda;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.repository.HistoricoCobrancaRepository;
import br.com.claricejoias_ws.repository.LeadRepository;
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
    private final AutenticacaoService autenticacaoService;

    // Injetar o serviço do WhatsApp
    private final WhatsAppService whatsAppService;

    public List<ClienteResponseDTO> listarTodos() {
        return clienteRepository.findAll().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    public List<ClienteResponseDTO> listarPendentes() {
        return clienteRepository.findClientesInadimplentes().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void registrarCobranca(Long clienteId, String funcionario) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));

        // 1. Calcular a dívida total do cliente para colocar na mensagem
        double totalDevido = 0.0;
        if (cliente.getVendas() != null) {
            totalDevido = cliente.getVendas().stream()
                    .filter(v -> v.getValorDevido() != null)
                    .mapToDouble(Venda::getValorDevido)
                    .sum();
        }

        // 2. Se a dívida for zero, não faz sentido cobrar
        if (totalDevido <= 0) {
            throw new RuntimeException("Este cliente não tem saldo devedor.");
        }

        // 3. Montar a mensagem de cobrança
        String valorFormatado = String.format("%.2f", totalDevido).replace(".", ",");
        String mensagem = "Olá *" + cliente.getNome() + "*, tudo bem?\n\n" +
                "Aqui é da *Clarice Joias* 💎.\n" +
                "Consta em nosso sistema um saldo pendente no valor de *R$ " + valorFormatado + "*.\n\n" +
                "Gostaria de verificar uma previsão de pagamento para podermos dar baixa no sistema? Qualquer dúvida, estamos à disposição!";

        // 1. Busca o Lead ou lança a sua exceção global automaticamente se não achar


        // 4. Disparar a mensagem pela Evolution API
        whatsAppService.enviarCobrancaCliente(cliente, mensagem, autenticacaoService.getUsername());

        // 5. Se o disparo não falhar, salva no histórico quem fez a cobrança
        HistoricoCobranca historico = new HistoricoCobranca();
        historico.setCliente(cliente);
        historico.setFuncionario(funcionario);
        historico.setDataHora(LocalDateTime.now());

        historicoCobrancaRepository.save(historico);
    }

    private ClienteResponseDTO converterParaDTO(Cliente cliente) {
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setId(cliente.getId());
        dto.setNome(cliente.getNome());
        dto.setTelefone(cliente.getTelefone());

        double totalDevido = 0.0;
        if (cliente.getVendas() != null) {
            totalDevido = cliente.getVendas().stream()
                    .filter(v -> v.getValorDevido() != null)
                    .mapToDouble(Venda::getValorDevido)
                    .sum();
        }
        dto.setValorDevido(totalDevido);

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