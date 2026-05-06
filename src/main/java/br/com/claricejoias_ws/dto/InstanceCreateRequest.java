package br.com.claricejoias_ws.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InstanceCreateRequest(
        String instanceName,
        String token,
        boolean qrcode,
        String integration, // <-- Mudou de Integration para String
        String webhook
) {
}
