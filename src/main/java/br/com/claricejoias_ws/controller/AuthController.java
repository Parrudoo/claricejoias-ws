package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.service.KeycloakAuthService;
import br.com.claricejoias_ws.service.KeycloakUserService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private KeycloakAuthService authService;

    @Autowired
    private KeycloakUserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciais) {
        try {
            String email = credenciais.get("email");
            String senha = credenciais.get("senha");
            Map<String, Object> tokens = authService.realizarLogin(email, senha);
            return ResponseEntity.ok(tokens);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.singletonMap("erro", e.getMessage()));
        }
    }

    @Operation(summary = "Cadastrar novo cliente", description = "Cria a conta no Keycloak, cria o perfil no banco local e vincula o histórico do visitante (Lead).")
    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(
            @RequestBody Map<String, String> dados,
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId) { // 1. Recebendo o ID do visitante
        try {

            // 2. Repassando o visitorId para o serviço sincronizar tudo no banco de dados!
            userService.criarUsuarioCliente(
                    dados.get("email"),
                    dados.get("senha"),
                    dados.get("nome"),
                    dados.get("whatsapp"),
                    visitorId
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("mensagem", "Conta criada com sucesso!"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("erro", e.getMessage()));
        }
    }
}