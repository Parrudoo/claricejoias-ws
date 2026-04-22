package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.service.KeycloakAuthService;
import br.com.claricejoias_ws.service.KeycloakUserService;
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

    // O login continua igual, mas o ideal no React (SPA) é que o React faça o login direto no Keycloak!
    // Se o seu React já está redirecionando pra tela preta do Keycloak, VOCÊ NÃO PRECISA DESSE MÉTODO.
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

    // O cadastro dos clientes via botão do site fica aqui!
    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(@RequestBody Map<String, String> dados) {
        try {
            // O serviço precisa ser ajustado para garantir que a Role "cliente" seja dada!
            userService.criarUsuarioCliente(dados.get("email"), dados.get("senha"), dados.get("nome"));
            return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("mensagem", "Conta criada com sucesso!"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("erro", e.getMessage()));
        }
    }
}