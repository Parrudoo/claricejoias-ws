package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.service.KeycloakAuthService;
import br.com.claricejoias_ws.service.KeycloakUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private KeycloakAuthService authService;

    @Autowired
    private KeycloakUserService userService; // Aquele que criamos antes

    // Recebe uma classe genérica só pra mapear o JSON do React (Crie esse DTO ou use um Map)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciais) {
        try {
            String email = credenciais.get("email");
            String senha = credenciais.get("senha");

            Map<String, Object> tokens = authService.realizarLogin(email, senha);
            return ResponseEntity.ok(tokens);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(@RequestBody Map<String, String> dados) {
        try {
            userService.criarUsuarioCliente(dados.get("email"), dados.get("senha"), dados.get("nome"));
            return ResponseEntity.status(HttpStatus.CREATED).body("Conta criada com sucesso!");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}