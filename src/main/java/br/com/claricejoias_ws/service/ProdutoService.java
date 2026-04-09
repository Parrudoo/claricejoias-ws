package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    public Produto salvar(Produto produto, MultipartFile file) throws Exception {
        if (file != null && !file.isEmpty()) {
            String objectName = minioService.upload(file);
            produto.setPathImg(objectName);
        }
        return produtoRepository.save(produto);
    }

    public Produto atualizar(Long id, Produto produtoAtualizado, MultipartFile file) throws Exception {
        return produtoRepository.findById(id).map(produto -> {
            produto.setNome(produtoAtualizado.getNome());
            produto.setPreco(produtoAtualizado.getPreco());
            produto.setMaterial(produtoAtualizado.getMaterial());
            // produto.setSubcategoria(produtoAtualizado.getSubcategoria());

            // Se enviou uma nova imagem, deleta a velha e faz upload da nova
            if (file != null && !file.isEmpty()) {
                try {
                    if (produto.getPathImg() != null) {
                        minioService.delete(produto.getPathImg());
                    }
                    String objectName = minioService.upload(file);
                    produto.setPathImg(objectName);
                } catch (Exception e) {
                    throw new RuntimeException("Erro ao fazer upload da nova imagem", e);
                }
            }
            return produtoRepository.save(produto);
        }).orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));
    }

    public void deletar(Long id) {
        produtoRepository.findById(id).ifPresent(produto -> {
            // Remove a imagem do MinIO antes de deletar do banco
            if (produto.getPathImg() != null) {
                try {
                    minioService.delete(produto.getPathImg());
                } catch (Exception e) {
                    log.error("Erro ao deletar imagem do MinIO: {}", produto.getPathImg(), e);
                }
            }
            produtoRepository.delete(produto);
        });
    }
}