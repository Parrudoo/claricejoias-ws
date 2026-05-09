package br.com.claricejoias_ws.controller;

import br.com.claricejoias_ws.exceptions.RegraNegocioException;
import br.com.claricejoias_ws.model.Cliente;
import br.com.claricejoias_ws.repository.ClienteRepository;
import br.com.claricejoias_ws.service.EvolutionApiService;
import br.com.claricejoias_ws.service.KeycloakAuthService;
import br.com.claricejoias_ws.service.KeycloakUserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {


    private final KeycloakAuthService authService;
    private final KeycloakUserService userService;


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
                    dados.get("whatsapp")
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("mensagem", "Conta criada com sucesso!"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("erro", e.getMessage()));
        }
    }


    @PostMapping("/recuperar-senha")
    public ResponseEntity<?> recuperarSenha(@RequestParam String whatsapp) {
        try {
            // Chama o serviço que faz a mágica (Keycloak + Evolution API)
            userService.recuperarSenhaViaWhatsApp(whatsapp);

            // Retorna 200 OK para o React saber que deu certo
            return ResponseEntity.ok(Map.of("mensagem", "Senha provisória enviada com sucesso!"));

        } catch (RegraNegocioException e) {
            // Se o WhatsApp não existir no banco, cai aqui e devolve 400 Bad Request
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));

        } catch (Exception e) {
            // Se der pau no Keycloak ou no Evolution API, devolve erro 500
            System.err.println("Erro ao recuperar senha: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("erro", "Ocorreu um erro interno ao processar a solicitação."));
        }
    }

}