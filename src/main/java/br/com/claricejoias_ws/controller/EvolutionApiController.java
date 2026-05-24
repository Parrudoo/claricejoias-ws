package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.dto.InstanceCreateRequest;
import br.com.claricejoias_ws.service.EvolutionApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/whatsapp/instances")
public class EvolutionApiController {

    private final EvolutionApiService evolutionApiService;

    @PostMapping
    public ResponseEntity<String> create(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) String revendedorId) {
        boolean isLojaMatriz = ( revendedorId == null) || (revendedorId.trim().isEmpty());

        // O ID do Keycloak é a fonte da verdade
        String usuarioId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        // O Backend decide os dados da instância, não o frontend!
        return evolutionApiService.createInstanceForUser(usuarioId, username,isLojaMatriz);
    }

    private boolean verificarSeAdmin(Jwt jwt) {
        // Estrutura padrão quando se usa Keycloak
        if (jwt.hasClaim("realm_access")) {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                // Verifique o nome exato da sua role (pode ser "admin", "ROLE_ADMIN", etc)
                return roles.contains("ROLE_ADMIN") || roles.contains("admin") || roles.contains("ADMIN");
            }
        }

        // Caso você mapeie as authorities de outra forma, pode verificar assim:
        // jwt.getClaimAsStringList("roles").contains("ROLE_ADMIN");

        return false;
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
        String usuarioId = jwt.getSubject();
        return evolutionApiService.fetchInstances(usuarioId);
    }

    @DeleteMapping("/my-instance/logout")
    public ResponseEntity<String> logoutMyInstance(@AuthenticationPrincipal Jwt jwt) {
        String usuarioId = jwt.getSubject();
        return evolutionApiService.logoutInstanceByUser(usuarioId);
    }
}