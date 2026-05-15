package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.InstanceCreateRequest;
import br.com.claricejoias_ws.service.EvolutionApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/whatsapp/instances")
public class EvolutionApiController {

    private final EvolutionApiService evolutionApiService;

    @PostMapping
    public ResponseEntity<String> create(@AuthenticationPrincipal Jwt jwt) {
        // O ID do Keycloak é a fonte da verdade
        String usuarioId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");

        // O Backend decide os dados da instância, não o frontend!
        return evolutionApiService.createInstanceForUser(usuarioId, username);
    }

    @GetMapping("/my-instance/connect")
    public ResponseEntity<String> connectMyInstance(@AuthenticationPrincipal Jwt jwt) {
        String usuarioId = jwt.getSubject();
        return evolutionApiService.connectInstanceByUser(usuarioId);
    }

    @DeleteMapping("/my-instance")
    public ResponseEntity<String> deleteMyInstance(@AuthenticationPrincipal Jwt jwt) {
        String usuarioId = jwt.getSubject();
        return evolutionApiService.deleteInstanceByUser(usuarioId);
    }

    // Apenas ADMINS deveriam acessar listarTodas
    @GetMapping(produces = "application/json")
    public ResponseEntity<String> listarTodas(@AuthenticationPrincipal Jwt jwt) {
        // Implementar validação de ROLE (ex: ROLE_ADMIN) aqui
        return evolutionApiService.fetchInstances();
    }

    @DeleteMapping("/my-instance/logout")
    public ResponseEntity<String> logoutMyInstance(@AuthenticationPrincipal Jwt jwt) {
        String usuarioId = jwt.getSubject();
        return evolutionApiService.logoutInstanceByUser(usuarioId);
    }
}