package br.com.claricejoias_ws.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricasLeadDTO {
    private long totalLeads;
    private long leadsConvertidos;
    private double taxaConversao; // Ex: 15.5 (%)
}