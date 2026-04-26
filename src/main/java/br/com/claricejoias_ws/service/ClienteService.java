package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.BaixaPagamentoDTO;
import br.com.claricejoias_ws.dto.ClienteResponseDTO;
import br.com.claricejoias_ws.dto.CompraDetalheDTO;
import br.com.claricejoias_ws.dto.MovimentacaoDTO;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.model.HistoricoCobranca;
import br.com.claricejoias_ws.model.Pagamento;
import br.com.claricejoias_ws.model.Venda;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.repository.HistoricoCobrancaRepository;
import br.com.claricejoias_ws.repository.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final HistoricoCobrancaRepository historicoCobrancaRepository;
    private final AutenticacaoService autenticacaoService;
    private final PagamentoRepository pagamentoRepository;
    private final WhatsAppService whatsAppService;

    // Adicionado Transactional readOnly para otimizar a leitura
    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> listarTodos() {
        List<Cliente> clientes = clienteRepository.findAll();
        List<ClienteResponseDTO> list = clienteRepository.findAll().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
        return list;
    }

    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> listarPendentes() {
        return clienteRepository.findClientesInadimplentes().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void registrarCobranca(Long clienteId, String funcionario) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));

        // 1. Puxar a dívida diretamente do novo campo de saldo do cliente
        double totalDevido = cliente.getSaldoDevedor() != null ? cliente.getSaldoDevedor().doubleValue() : 0.0;

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

        // 4. Disparar a mensagem pela Evolution API
        whatsAppService.enviarCobrancaCliente(cliente, mensagem, autenticacaoService.getUsername());

        // 5. Se o disparo não falhar, salva no histórico quem fez a cobrança
        HistoricoCobranca historico = new HistoricoCobranca();
        historico.setCliente(cliente);
        historico.setFuncionario(funcionario);
        historico.setDataHora(LocalDateTime.now());

        historicoCobrancaRepository.save(historico);
    }

    // Adicionado Transactional pois precisamos carregar a lista de vendas (Lazy)
    @Transactional(readOnly = true)
    public List<MovimentacaoDTO> buscarHistoricoCompras(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));

        List<MovimentacaoDTO> extrato = new ArrayList<>();

        // Inclui as Compras (🛒) mantendo seus detalhes originais
        if (cliente.getVendas() != null) {
            cliente.getVendas().forEach(v -> extrato.add(
                    MovimentacaoDTO.builder()
                            .tipo("COMPRA")
                            .data(v.getDataVenda())
                            .valor(v.getTotal())
                            .metodo(v.getMetodoPagamento())
                            .valorEntrada(v.getValorEntrada())
                            .parcelas(v.getParcelas())
                            .build()
            ));
        }

        // Inclui os Pagamentos (💰) que o cliente já fez
        if (cliente.getPagamentos() != null) {
            cliente.getPagamentos().forEach(p -> extrato.add(
                    MovimentacaoDTO.builder()
                            .tipo("PAGAMENTO")
                            .data(p.getDataPagamento().atStartOfDay())
                            .valor(p.getValorPago().doubleValue())
                            .metodo(p.getFormaPagamento())
                            .observacao(p.getObservacao())
                            .build()
            ));
        }

        // Ordena do mais recente para o mais antigo
        extrato.sort(Comparator.comparing(MovimentacaoDTO::getData).reversed());
        return extrato;
    }

    // ==========================================================
    // CONVERSORES (Mappers)
    // ==========================================================
    private ClienteResponseDTO converterParaDTO(Cliente cliente) {
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setId(cliente.getId());
        dto.setNome(cliente.getNome());
        dto.setTelefone(cliente.getTelefone());

        // 1. Soma o valor devido de todas as vendas
        double totalVendasFiado = 0.0;
        if (cliente.getVendas() != null) {
            totalVendasFiado = cliente.getVendas().stream()
                    .filter(v -> v.getValorDevido() != null)
                    .mapToDouble(Venda::getValorDevido)
                    .sum();
        }

        // 2. Soma todos os pagamentos (baixas) que o cliente já fez
        double totalPagamentosRealizados = 0.0;
        if (cliente.getPagamentos() != null) {
            totalPagamentosRealizados = cliente.getPagamentos().stream()
                    .filter(p -> p.getValorPago() != null)
                    .mapToDouble(p -> p.getValorPago().doubleValue())
                    .sum();
        }

        // 3. O saldo real é a diferença
        double saldoReal = totalVendasFiado - totalPagamentosRealizados;

        // Garante que o saldo não fique negativo na tela por arredondamento
        dto.setValorDevido(Math.max(0, saldoReal));

        // Histórico de cobrança...
        historicoCobrancaRepository.findFirstByClienteIdOrderByDataHoraDesc(cliente.getId())
                .ifPresent(historico -> {
                    ClienteResponseDTO.UltimaCobrancaDTO cobrancaDTO = new ClienteResponseDTO.UltimaCobrancaDTO();
                    cobrancaDTO.setDataHora(historico.getDataHora());
                    cobrancaDTO.setFuncionario(historico.getFuncionario());
                    dto.setUltimaCobranca(cobrancaDTO);
                });

        return dto;
    }

    private CompraDetalheDTO converterVendaParaCompraDetalheDTO(Venda venda) {
        return CompraDetalheDTO.builder()
                .id(venda.getId())
                .data(venda.getDataVenda())
                .total(venda.getTotal())
                .metodoPagamento(venda.getMetodoPagamento())
                .valorEntrada(venda.getValorEntrada() != null ? venda.getValorEntrada() : 0.0)
                .parcelas(venda.getParcelas() != null ? venda.getParcelas() : 1)
                .build();
    }

    @Transactional
    public void registrarPagamento(Long clienteId, BaixaPagamentoDTO dto) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado com ID: " + clienteId));

        // 1. Calcular o saldo devedor real somando as vendas fiadas
        BigDecimal saldoRealCalculado = BigDecimal.ZERO;
        if (cliente.getVendas() != null) {
            double somaVendas = cliente.getVendas().stream()
                    .filter(v -> v.getValorDevido() != null)
                    .mapToDouble(Venda::getValorDevido)
                    .sum();
            saldoRealCalculado = BigDecimal.valueOf(somaVendas);
        }

        // 2. Validações de negócio
        if (dto.valorPago() == null || dto.valorPago().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("O valor do pagamento deve ser maior que zero.");
        }

        // Comparar com o saldo que acabamos de calcular das vendas
        if (dto.valorPago().compareTo(saldoRealCalculado) > 0) {
            throw new RegraNegocioException(
                    "O valor do pagamento não pode ser maior que o saldo devedor atual (R$ " + saldoRealCalculado + ")."
            );
        }

        // 3. Atualizar o saldo_devedor do cliente (o campo resumo no banco)
        // Mesmo que você use o cálculo das vendas no Dashboard, é bom manter esse campo
        // atualizado para performance futura.
        BigDecimal novoSaldo = saldoRealCalculado.subtract(dto.valorPago());
        cliente.setSaldoDevedor(novoSaldo);
        clienteRepository.save(cliente);

        // 4. Registrar o histórico financeiro
        Pagamento historico = new Pagamento();
        historico.setCliente(cliente);
        historico.setValorPago(dto.valorPago());
        historico.setFormaPagamento(dto.formaPagamento());
        historico.setObservacao(dto.observacao());
        historico.setDataPagamento(dto.dataPagamento() != null ? dto.dataPagamento() : LocalDate.now());

        pagamentoRepository.save(historico);

        // DICA: Se você quiser abater o valor diretamente das vendas (diminuindo o valorDevido delas),
        // seria necessário uma lógica extra para percorrer as vendas e ir subtraindo até zerar o valor pago.
    }

//    @Transactional(readOnly = true)
//    public List<MovimentacaoDTO> buscarExtratoCompleto(Long clienteId) {
//        Cliente cliente = clienteRepository.findById(clienteId)
//                .orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));
//
//        List<MovimentacaoDTO> historico = new ArrayList<>();
//
//        // Adiciona Compras
//        cliente.getVendas().forEach(v -> {
//            historico.add(new MovimentacaoDTO(
//                    "COMPRA", v.getDataVenda(), v.getTotal(), v.getMetodoPagamento(), null
//            ));
//        });
//
//        // Adiciona Pagamentos (Baixas)
//        cliente.getPagamentos().forEach(p -> {
//            historico.add(new MovimentacaoDTO(
//                    "PAGAMENTO", p.getDataPagamento().atStartOfDay(), p.getValorPago().doubleValue(), p.getFormaPagamento(), null
//            ));
//        });
//
//        // Ordena por data (mais recente primeiro)
//        return historico.stream()
//                .sorted(Comparator.comparing(MovimentacaoDTO::data).reversed())
//                .collect(Collectors.toList());
//    }
}