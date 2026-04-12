package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ModelMapper mapper;

    @Autowired
    private MinioService minioService;

    public List<ProdutoDTO> listarTodos() {
        return produtoRepository.findAll().stream()
                .map(p -> mapper.map(p, ProdutoDTO.class))
                .toList();
    }

    public Optional<Produto> buscarPorId(Long id) {
        return produtoRepository.findById(id);
    }

    // Recebe o arquivo junto com o produto
    public Produto salvar(Produto produto, List<MultipartFile> files) throws Exception {

        // Verifica se a lista de arquivos não é nula e não está vazia
        if (files != null && !files.isEmpty()) {

            // Cria uma lista vazia para guardar as URLs/Paths que o MinIO vai devolver
            List<String> caminhosImagens = new ArrayList<>();

            // Passa por cada arquivo recebido do React
            for (MultipartFile file : files) {
                // Se o arquivo não estiver vazio (garantia extra)
                if (!file.isEmpty()) {
                    // Faz o upload de UM arquivo por vez
                    String objectName = minioService.upload(file);
                    // Adiciona o caminho retornado na nossa lista
                    caminhosImagens.add(objectName);
                }
            }

            // Em vez de setPathImg, agora você precisa setar uma lista
            produto.setImagens(caminhosImagens);
        }

        return produtoRepository.save(produto);
    }

    public Produto atualizar(Long id, Produto produtoAtualizado, List<MultipartFile> files) throws Exception {
        return produtoRepository.findById(id).map(produto -> {
            produto.setNome(produtoAtualizado.getNome());
            produto.setPreco(produtoAtualizado.getPreco());
            produto.setMaterial(produtoAtualizado.getMaterial());
            // produto.setSubcategoria(produtoAtualizado.getSubcategoria());

            // Se o usuário enviou arquivos novos na hora de editar
            if (files != null && !files.isEmpty()) {
                try {
                    // 1. Deleta as imagens antigas do MinIO para não ocupar espaço à toa
                    if (produto.getImagens() != null && !produto.getImagens().isEmpty()) {
                        for (String imagemAntiga : produto.getImagens()) {
                            minioService.delete(imagemAntiga);
                        }
                        // Limpa a lista velha do banco
                        produto.getImagens().clear();
                    }

                    // 2. Faz o upload das imagens novas
                    List<String> novasImagens = new ArrayList<>();
                    for (MultipartFile file : files) {
                        if (!file.isEmpty()) {
                            String objectName = minioService.upload(file);
                            novasImagens.add(objectName);
                        }
                    }

                    // 3. Salva a nova lista de links no produto
                    produto.setImagens(novasImagens);

                } catch (Exception e) {
                    throw new RuntimeException("Erro ao atualizar as imagens da joia", e);
                }
            }

            return produtoRepository.save(produto);
        }).orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));
    }

    public void deletar(Long id) {
        produtoRepository.findById(id).ifPresent(produto -> {

            // Verifica se a lista de imagens não é nula e não está vazia
            if (produto.getImagens() != null && !produto.getImagens().isEmpty()) {

                // Passa por cada imagem da lista e deleta do MinIO
                for (String imagem : produto.getImagens()) {
                    try {
                        minioService.delete(imagem);
                    } catch (Exception e) {
                        // Loga o erro, mas o loop continua para tentar apagar as próximas
                        log.error("Erro ao deletar imagem do MinIO: {}", imagem, e);
                    }
                }
            }

            // Após limpar os arquivos físicos, deleta o registro do banco de dados
            produtoRepository.delete(produto);
        });
    }
}