package br.com.claricejoias_ws.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AutenticacaoService {

    /**
     * Retorna o Token JWT completo da requisição atual.
     */
    public Jwt getJwtToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof Jwt) {
            return (Jwt) auth.getPrincipal();
        }
        return null;
    }

    /**
     * Retorna o ID único do usuário no Keycloak.
     */
    public String getUsuarioId() {
        Jwt jwt = getJwtToken();
        return jwt != null ? jwt.getSubject() : null;
    }

    /**
     * Retorna o nome de usuário.
     */
    public String getUsername() {
        Jwt jwt = getJwtToken();
        return jwt != null ? jwt.getClaimAsString("preferred_username") : null;
    }

    /**
     * Retorna o email do usuário.
     */
    public String getEmail() {
        Jwt jwt = getJwtToken();
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    /**
     * Verifica se o usuário atual tem uma role específica.
     */
    public boolean temRole(String roleName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;

        return auth.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_" + roleName));
    }
}