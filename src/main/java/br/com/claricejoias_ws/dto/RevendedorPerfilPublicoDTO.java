package br.com.claricejoias_ws.dto;

// Retornado pelo endpoint público /api/catalogo-publico/{slug}/perfil.
// Só expõe o que a vitrine precisa exibir — nunca a WhatsappInstance
// (que carrega o uniqueToken da Evolution API) nem dados comerciais internos.
public record RevendedorPerfilPublicoDTO(String id, String nome, String slug, String whatsappContato) {
}
