package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.AcertoRevendedorDTO;
import br.com.claricejoias_ws.enums.StatusPedido;
import br.com.claricejoias_ws.model.Pedido;
import br.com.claricejoias_ws.model.Revendedor;
import br.com.claricejoias_ws.repository.PedidoRepository;
import br.com.claricejoias_ws.repository.RevendedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceiroRevendedorService {

    private final PedidoRepository pedidoRepository;
    private final RevendedorRepository revendedorRepository;

    public AcertoRevendedorDTO calcularAcertoMensal(String revendedorId, int mes, int ano) {
        Revendedor revendedor = revendedorRepository.findById(revendedorId)
                .orElseThrow(() -> new RuntimeException("Revendedor não encontrado"));

        // Monta as datas de Início e Fim do mês (Ex: 01/05/2026 00:00:00 até 31/05/2026 23:59:59)
        YearMonth anoMes = YearMonth.of(ano, mes);
        LocalDateTime dataInicio = anoMes.atDay(1).atStartOfDay();
        LocalDateTime dataFim = anoMes.atEndOfMonth().atTime(LocalTime.MAX);

        // Busca todas as vendas PAGAS do revendedor nesse mês
        List<Pedido> pedidosDoMes = pedidoRepository.findByRevendedorIdAndDataCriacaoBetweenAndStatus(
                revendedorId, dataInicio, dataFim, StatusPedido.PAGO
        );

        // Inicia os contadores
        BigDecimal totalVendido = BigDecimal.ZERO;
        BigDecimal lucroRevendedor = BigDecimal.ZERO;

        // Soma os valores
        for (Pedido p : pedidosDoMes) {
            totalVendido = totalVendido.add(p.getTotal() != null ? p.getTotal() : BigDecimal.ZERO);
            lucroRevendedor = lucroRevendedor.add(p.getComissaoRevendedor() != null ? p.getComissaoRevendedor() : BigDecimal.ZERO);
        }

        // O repasse para a matriz é simplesmente o (Total Vendido - A Parte da Revendedora)
        BigDecimal repasseMatriz = totalVendido.subtract(lucroRevendedor);

        // Monta o DTO de resposta
        AcertoRevendedorDTO dto = new AcertoRevendedorDTO();
        dto.setRevendedorId(revendedor.getId());
        dto.setRevendedorNome(revendedor.getNome());
        dto.setQuantidadeVendas(pedidosDoMes.size());
        dto.setTotalVendido(totalVendido);
        dto.setLucroRevendedor(lucroRevendedor);
        dto.setRepasseMatriz(repasseMatriz);

        return dto;
    }
}