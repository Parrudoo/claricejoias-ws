package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.*;
import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.*;
import br.com.claricejoias_ws.repository.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final HistoricoCobrancaRepository historicoCobrancaRepository;
    private final AutenticacaoService autenticacaoService;
    private final PagamentoRepository pagamentoRepository;
    private final WhatsAppService whatsAppService;
    private final VendaRepository vendaRepository;
    private final ParcelaRepository parcelaRepository;
    private final LeadRepository leadRepository;


    // Adicionado Transactional readOnly para otimizar a leitura
    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> listarTodos() {
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
    @Retryable(
            retryFor = {
                    DataIntegrityViolationException.class,
                    ObjectOptimisticLockingFailureException.class,
                    StaleObjectStateException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 150)
    )
    public Cliente sincronizarClienteComKeycloak(Jwt jwt, String visitorId) {
        String usuarioId = jwt.getSubject(); // Pega o ID do Keycloak
        String email = jwt.getClaimAsString("email");
        String nome = jwt.getClaimAsString("name"); // Ou "preferred_username" (depende da conf. do Keycloak)

        // 1. Verifica se o cliente já existe no banco. Se sim, apenas o retorna.
        return clienteRepository.findByUsuarioId(usuarioId).orElseGet(() -> {

            // 2. Não existe! É o primeiro login após criar a conta.
            Cliente novoCliente = new Cliente();
            novoCliente.setUsuarioId(usuarioId);
            novoCliente.setEmail(email);
            novoCliente.setNome(nome);

            // =========================================================
            // 3. A MÁGICA DO FUNIL DE MARKETING (LEAD)
            //            // =========================================================
            Lead leadDoMarketing = null;

            // Tenta achar se ele já era um Lead capturado (pelo visitorId)
            if (visitorId != null && !visitorId.isEmpty()) {
                leadDoMarketing = leadRepository.findByVisitorId(visitorId).orElse(null);
            }

            if (leadDoMarketing == null) {
                // CENÁRIO A: O cara ignorou a isca digital e logou direto.
                // Criamos um Lead silenciosamente para ele entrar na campanha de WhatsApp!
                leadDoMarketing = new Lead();
                leadDoMarketing.setVisitorId(visitorId != null ? visitorId : UUID.randomUUID().toString());
                leadDoMarketing.setUsuarioId(usuarioId);
                leadDoMarketing.setNome(nome);
                leadDoMarketing.setEmail(email);
            } else {
                // CENÁRIO B: Ele já era Lead (baixou o E-book/Guia antes).
                // Atualizamos os dados dele com as informações oficiais e validadas do Keycloak
                leadDoMarketing.setUsuarioId(usuarioId);
                leadDoMarketing.setEmail(email);

                if (nome != null && !nome.isEmpty()) {
                    leadDoMarketing.setNome(nome);
                }

                // Se ele deixou o WhatsApp lá atrás na captura, nós copiamos para o perfil do Cliente oficial!
                if (leadDoMarketing.getWhatsapp() != null) {
                    novoCliente.setWhatsapp(leadDoMarketing.getWhatsapp());
                }

                // Se o Keycloak não enviou o nome, mas ele preencheu no Guia, a gente aproveita
                if (novoCliente.getNome() == null && leadDoMarketing.getNome() != null) {
                    novoCliente.setNome(leadDoMarketing.getNome());
                }
            }

            // 4. Salva o Lead (seja ele atualizado ou novinho em folha)
            // Isso garante que todo cliente vai aparecer no seu LeadsDashboard!
            leadRepository.save(leadDoMarketing);

            // 5. Salva o cliente oficial no banco
            return clienteRepository.save(novoCliente);
        });
    }

    @Transactional
    public void registrarCobranca(Long clienteId, String funcionario) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));

        // 1. Calcular a dívida real varrendo as vendas e somando as parcelas PENDENTES
        BigDecimal totalDevido = cliente.getVendas().stream()
                .flatMap(venda -> venda.getParcelasDetalhadas().stream())
                // Filtra rigorosamente pelas parcelas que têm o status PENDENTE
                .filter(parcela -> "PENDENTE".equalsIgnoreCase(parcela.getStatus()))
                // Puxa o BigDecimal nativo, previnindo valores nulos com BigDecimal.ZERO
                .map(parcela -> parcela.getValor() != null ? parcela.getValor() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Se a dívida for zero, bloqueia a cobrança
        if (totalDevido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Este cliente não possui vendas com parcelas pendentes.");
        }

        // 3. Formatar o valor e montar a mensagem de cobrança
        // O Locale pt-BR já formata automaticamente com vírgula (ex: 1500,50)
        String valorFormatado = String.format(new Locale("pt", "BR"), "%.2f", totalDevido);

        String mensagem = "Olá *" + cliente.getNome() + "*, tudo bem?\n\n" +
                "Aqui é da *Clarice Joias* 💎.\n" +
                "Consta em nosso sistema um saldo pendente no valor de *R$ " + valorFormatado + "*.\n\n" +
                "Gostaria de verificar uma previsão de pagamento para podermos dar baixa no sistema? Qualquer dúvida, estamos à disposição!";

        // 4. Disparar a mensagem pela Evolution API
        whatsAppService.enviarCobrancaCliente(cliente, mensagem, autenticacaoService.getUsername());

        // 5. Registrar o histórico da ação
        HistoricoCobranca historico = new HistoricoCobranca();
        historico.setCliente(cliente);
        historico.setFuncionario(funcionario);
        historico.setDataHora(LocalDateTime.now());

        historicoCobrancaRepository.save(historico);
    }

    // Adicionado Transactional pois precisamos carregar a lista de vendas (Lazy)
    public List<MovimentacaoDTO> buscarHistoricoCompras(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));

        List<MovimentacaoDTO> extrato = new ArrayList<>();

        if (cliente.getVendas() != null) {
            cliente.getVendas().forEach(venda -> {

                // 1. Mapeia os pagamentos vinculados EXCLUSIVAMENTE a esta venda
                List<PagamentoDTO> pagamentosDaVenda = new ArrayList<>();
                if (venda.getPagamentos() != null) {
                    venda.getPagamentos().forEach(p -> pagamentosDaVenda.add(
                            PagamentoDTO.builder()
                                    .data(p.getDataPagamento().atStartOfDay())
                                    .valor(p.getValorPago())
                                    .metodo(p.getFormaPagamento())
                                    .observacao(p.getObservacao())
                                    .build()
                    ));
                }
                // Ordenar os pagamentos dentro da compra do mais recente para o mais antigo
                pagamentosDaVenda.sort(Comparator.comparing(PagamentoDTO::getData).reversed());


                // 2. Mapeia as parcelas detalhadas da Venda (Apenas para FIADO)
                List<ParcelaDTO> listaParcelasDTO = new ArrayList<>();
                if (venda.getParcelasDetalhadas() != null && !venda.getParcelasDetalhadas().isEmpty()) {
                    listaParcelasDTO = venda.getParcelasDetalhadas().stream().map(p ->
                            ParcelaDTO.builder()
                                    .id(p.getId())
                                    .numeroParcela(p.getNumeroParcela())
                                    .valor(p.getValor())
                                    .dataVencimento(p.getDataVencimento())
                                    .dataPagamento(p.getDataPagamento())
                                    .status(p.getStatus())
                                    .build()
                    ).collect(Collectors.toList());
                }

                // 3. Monta o DTO da Compra e anexa as listas nela
                extrato.add(
                        MovimentacaoDTO.builder()
                                .tipo("COMPRA")
                                .data(venda.getDataVenda())
                                .valor(venda.getTotal())
                                .metodo(venda.getMetodoPagamento())
                                .valorEntrada(venda.getValorEntrada())
                                .valorDevido(venda.getValorDevido())
                                .qtdParcelas(venda.getParcelas())
                                .parcelas(listaParcelasDTO)
                                .historicoPagamentos(pagamentosDaVenda)
                                .build()
                );
            });
        }

        // 4. Ordena as compras da mais recente para a mais antiga
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
        dto.setTelefone(cliente.getWhatsapp());

        // 1. Soma o valor devido de todas as vendas (Tudo limpo e direto)
        BigDecimal totalVendasFiado = BigDecimal.ZERO;
        if (cliente.getVendas() != null) {
            totalVendasFiado = cliente.getVendas().stream()
                    .filter(v -> v.getValorDevido() != null)
                    .map(Venda::getValorDevido) // Mapeia direto o BigDecimal
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // 2. Soma todos os pagamentos realizados
        BigDecimal totalPagamentosRealizados = BigDecimal.ZERO;
        if (cliente.getPagamentos() != null) {
            totalPagamentosRealizados = cliente.getPagamentos().stream()
                    .filter(p -> p.getValorPago() != null)
                    .map(Pagamento::getValorPago) // Mapeia direto o BigDecimal
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        if (cliente.getVendas() != null) {
            List<ClienteResponseDTO.VendaResponseDTO> vendasDTO = cliente.getVendas().stream()
                    .map(venda -> {
                        ClienteResponseDTO.VendaResponseDTO vDto = new ClienteResponseDTO.VendaResponseDTO();
                        vDto.setId(venda.getId());
                        vDto.setDataVenda(venda.getDataVenda());
                        vDto.setTotal(venda.getTotal());
                        vDto.setMetodoPagamento(venda.getMetodoPagamento());
                        vDto.setValorEntrada(venda.getValorEntrada());
                        vDto.setValorDevido(venda.getValorDevido());

                        // Mapeia as parcelas desta venda
                        if (venda.getParcelasDetalhadas() != null) {
                            List<ClienteResponseDTO.ParcelaResponseDTO> parcelasDTO = venda.getParcelasDetalhadas().stream()
                                    .map(parcela -> {
                                        ClienteResponseDTO.ParcelaResponseDTO pDto = new ClienteResponseDTO.ParcelaResponseDTO();
                                        pDto.setId(parcela.getId());
                                        pDto.setNumeroParcela(parcela.getNumeroParcela());
                                        pDto.setValor(parcela.getValor());
                                        pDto.setDataVencimento(parcela.getDataVencimento());
                                        pDto.setDataPagamento(parcela.getDataPagamento());
                                        pDto.setStatus(parcela.getStatus());
                                        return pDto;
                                    }).collect(Collectors.toList());
                            vDto.setParcelas(parcelasDTO);
                        }

                        return vDto;
                    }).collect(Collectors.toList());

            dto.setVendas(vendasDTO);
        }

        // 3. Calcula o saldo real (Vendas - Pagamentos)
        BigDecimal saldoReal = totalVendasFiado.subtract(totalPagamentosRealizados);

        // Garante que o saldo não fique negativo na tela e joga no DTO
        dto.setValorDevido(saldoReal.max(BigDecimal.ZERO));

        // Histórico de cobrança
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
                // Aproveitei para colocar uma proteção contra nulo no total também, por segurança
                .total(venda.getTotal() != null ? venda.getTotal() : BigDecimal.ZERO)
                .metodoPagamento(venda.getMetodoPagamento())
                // 👇 Substituído 0.0 por BigDecimal.ZERO
                .valorEntrada(venda.getValorEntrada() != null ? venda.getValorEntrada() : BigDecimal.ZERO)
                .parcelas(venda.getParcelas() != null ? venda.getParcelas() : 1)
                .build();
    }



    @Transactional
    public void registrarPagamento(Long clienteId, BaixaPagamentoDTO dto) {

        // 👇 SE O FRONTEND MANDAR O ID DA PARCELA, EXECUTA O PAGAMENTO INDIVIDUAL
        if (dto.parcelaId() != null) {
            pagarParcelaEspecifica(dto.parcelaId(), dto);
            return; // Sai do método para não rodar a lógica FIFO
        }

        // ====================================================================
        // LÓGICA FIFO (O pagamento geral que você já tinha)
        // ====================================================================
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        BigDecimal valorPago = dto.valorPago();
        if (valorPago == null || valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("O valor do pagamento deve ser maior que zero.");
        }

        List<Venda> vendasPendentes = cliente.getVendas().stream()
                .filter(v -> v.getValorDevido() != null && v.getValorDevido().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(Venda::getDataVenda))
                .collect(Collectors.toList());

        BigDecimal montanteDisponivel = valorPago;

        for (Venda venda : vendasPendentes) {
            if (montanteDisponivel.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal dividaVenda = venda.getValorDevido();
            BigDecimal abateVenda = montanteDisponivel.min(dividaVenda);

            BigDecimal valorParaParcelas = abateVenda;
            List<Parcela> parcelasPendentes = venda.getParcelasDetalhadas().stream()
                    .filter(p -> "PENDENTE".equalsIgnoreCase(p.getStatus()))
                    .sorted(Comparator.comparing(Parcela::getNumeroParcela))
                    .collect(Collectors.toList());

            for (Parcela parcela : parcelasPendentes) {
                if (valorParaParcelas.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal valorParcela = parcela.getValor();

                if (valorParaParcelas.compareTo(valorParcela) >= 0) {
                    parcela.setStatus("PAGA");
                    parcela.setDataPagamento(dto.dataPagamento() != null ? dto.dataPagamento() : LocalDate.now());
                    valorParaParcelas = valorParaParcelas.subtract(valorParcela);
                } else {
                    parcela.setValor(valorParcela.subtract(valorParaParcelas));
                    valorParaParcelas = BigDecimal.ZERO;
                }
            }

            venda.setValorDevido(dividaVenda.subtract(abateVenda));
            vendaRepository.save(venda);

            Pagamento historico = new Pagamento();
            historico.setCliente(cliente);
            historico.setVenda(venda);
            historico.setValorPago(abateVenda);
            historico.setFormaPagamento(dto.formaPagamento());
            historico.setDataPagamento(dto.dataPagamento() != null ? dto.dataPagamento() : LocalDate.now());
            historico.setObservacao(dto.observacao());
            pagamentoRepository.save(historico);

            montanteDisponivel = montanteDisponivel.subtract(abateVenda);
        }

        BigDecimal saldoAnterior = cliente.getSaldoDevedor() != null ? cliente.getSaldoDevedor() : BigDecimal.ZERO;
        cliente.setSaldoDevedor(saldoAnterior.subtract(valorPago).max(BigDecimal.ZERO));
        clienteRepository.save(cliente);
    }

    // 👇 NOVO MÉTODO AUXILIAR PARA PAGAR A PARCELA EXATA
    private void pagarParcelaEspecifica(Long parcelaId, BaixaPagamentoDTO dto) {
        Parcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        if ("PAGA".equalsIgnoreCase(parcela.getStatus())) {
            throw new RuntimeException("Esta parcela já está paga.");
        }

        // 1. Marca a parcela como PAGA
        parcela.setStatus("PAGA");
        parcela.setDataPagamento(dto.dataPagamento() != null ? dto.dataPagamento() : LocalDate.now());
        parcelaRepository.save(parcela);

        // 2. Abate o valor na Venda associada
        Venda venda = parcela.getVenda();
        venda.setValorDevido(venda.getValorDevido().subtract(parcela.getValor()).max(BigDecimal.ZERO));
        vendaRepository.save(venda);

        // 3. Abate o valor na dívida geral do Cliente
        Cliente cliente = venda.getCliente();
        BigDecimal saldoAnterior = cliente.getSaldoDevedor() != null ? cliente.getSaldoDevedor() : BigDecimal.ZERO;
        cliente.setSaldoDevedor(saldoAnterior.subtract(parcela.getValor()).max(BigDecimal.ZERO));
        clienteRepository.save(cliente);

        // 4. Salva o recibo (Histórico)
        Pagamento historico = new Pagamento();
        historico.setCliente(cliente);
        historico.setVenda(venda);
        historico.setValorPago(parcela.getValor());
        historico.setFormaPagamento(dto.formaPagamento());
        historico.setDataPagamento(dto.dataPagamento() != null ? dto.dataPagamento() : LocalDate.now());

        // Anota na observação qual parcela foi paga manualmente
        String obs = dto.observacao() != null ? dto.observacao() : "";
        historico.setObservacao("Pagamento direto da " + parcela.getNumeroParcela() + "ª parcela. " + obs);

        pagamentoRepository.save(historico);
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