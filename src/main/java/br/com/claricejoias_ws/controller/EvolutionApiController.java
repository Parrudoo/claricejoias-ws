package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.InstanceCreateRequest;
import br.com.claricejoias_ws.service.EvolutionApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/whatsapp/instances")
public class EvolutionApiController {

    private final EvolutionApiService evolutionApiService;


    @PostMapping
    public ResponseEntity<String> create(@RequestBody InstanceCreateRequest request) {
        return evolutionApiService.createInstance(request);
    }

    @GetMapping("/{instanceName}/connect")
    public ResponseEntity<String> connect(@PathVariable String instanceName) {
        return evolutionApiService.connectInstance(instanceName);
    }

    @DeleteMapping("/{instanceName}")
    public ResponseEntity<String> delete(@PathVariable String instanceName) {
        return evolutionApiService.deleteInstance(instanceName);
    }

    @DeleteMapping("/{instanceName}/logout")
    public ResponseEntity<String> logout(@PathVariable String instanceName) {
        return evolutionApiService.logoutInstance(instanceName);
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<String> listarTodas() {
        return evolutionApiService.fetchInstances();
    }

    @PostMapping("/{instanceName}/webhook")
    public ResponseEntity<String> setWebhook(
            @PathVariable String instanceName,
            @RequestBody Map<String, Object> webhookConfig) {
        return evolutionApiService.setWebhook(instanceName, webhookConfig);
    }
}