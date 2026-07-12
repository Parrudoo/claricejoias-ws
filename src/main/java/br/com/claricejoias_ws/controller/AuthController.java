package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.service.EvolutionApiService;

import br.com.claricejoias_ws.service.KeycloakAuthService;
import br.com.claricejoias_ws.service.KeycloakUserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {


    private final KeycloakAuthService authService;
    private final KeycloakUserService userService;
    private final ClienteRepository clienteRepository;


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciais) {
        try {
            String email = credenciais.get("email");
            String senha = credenciais.get("senha");
            Map<String, Object> tokens = authService.realizarLogin(email, senha);
            return ResponseEntity.ok(tokens);
        } catch (RuntimeException e) {
            log.warn("Falha de login para o e-mail informado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.singletonMap("erro", "E-mail ou senha inválidos."));
        }
    }

    @Operation(summary = "Cadastrar novo cliente", description = "Cria a conta no Keycloak, cria o perfil no banco local e vincula o histórico do visitante (Lead).")
    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(
            @RequestBody Map<String, String> dados,
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId) { // 1. Recebendo o ID do visitante
        try {

            // 2. Cria no Keycloak + perfil local, e repassa o visitorId para vincular o histórico do Lead
            String userId = userService.criarUsuarioCliente(
                    dados.get("email"),
                    dados.get("senha"),
                    dados.get("nome"),
                    dados.get("whatsapp"),
                    dados.get("revendedorId")
            );

            clienteRepository.findByUsuarioId(userId).ifPresent(cliente ->
                    userService.vincularVisitanteAoNovoUsuario(visitorId, userId, cliente)
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("mensagem", "Conta criada com sucesso!"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("erro", e.getMessage()));
        }
    }


    @PostMapping("/recuperar-senha")
    public ResponseEntity<?> recuperarSenha(@RequestParam String whatsapp, @RequestParam(required = false) String revendedorId) {
        try {
            if (revendedorId != null) {
                userService.recuperarSenhaViaWhatsApp(whatsapp, revendedorId);
            } else {
                userService.recuperarSenhaViaWhatsAppMatriz(whatsapp);
            }

            // Retorna 200 OK para o React saber que deu certo
            return ResponseEntity.ok(Map.of("mensagem", "Senha provisória enviada com sucesso!"));

        } catch (RegraNegocioException e) {
            // Se o WhatsApp não existir no banco, cai aqui e devolve 400 Bad Request
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));

        } catch (Exception e) {
            // Se der pau no Keycloak ou no Evolution API, devolve erro 500
            log.error("Erro ao recuperar senha", e);
            return ResponseEntity.internalServerError().body(Map.of("erro", "Ocorreu um erro interno ao processar a solicitação."));
        }
    }

}