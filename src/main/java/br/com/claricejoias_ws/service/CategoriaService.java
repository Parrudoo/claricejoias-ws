package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.CategoriaDTO;
import br.com.claricejoias_ws.dto.ProdutoCatalogoDTO;
import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.dto.SubcategoriaDTO;
import br.com.claricejoias_ws.model.Categoria;
import br.com.claricejoias_ws.model.Subcategoria;
import br.com.claricejoias_ws.repository.CategoriaRepository;
import br.com.claricejoias_ws.repository.SubCategoriaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository repository;
    private final SubCategoriaRepository subCategoriaRepository;
    private final ModelMapper modelMapper;
    private final AutenticacaoService autenticacaoService;




        public List<CategoriaDTO> listarTodas() {
            return repository.findAll().stream()
                    // Chamamos um método próprio em vez do modelMapper direto
                    .map(this::converterCategoriaParaDTO)
                    .collect(Collectors.toList());
        }


    private CategoriaDTO converterCategoriaParaDTO(Categoria categoria) {
        CategoriaDTO dto = new CategoriaDTO();
        dto.setId(categoria.getId());
        dto.setNome(categoria.getNome());
        // (Se houver outros campos simples na categoria, adicione aqui)

        // Mapeamento à prova de balas para o Set de Subcategorias
        if (categoria.getSubcategorias() != null) {
            dto.setSubcategorias(categoria.getSubcategorias().stream()
                    .map(sub -> {
                        SubcategoriaDTO subDto = new SubcategoriaDTO();
                        subDto.setId(sub.getId());
                        subDto.setNome(sub.getNome());

                        // Se o seu SubcategoriaDTO tiver a lista de produtos, mapeie aqui:
                        if (sub.getItens() != null) {
                            subDto.setItens(sub.getItens().stream()
                                    // Como a joia não tem mais listas complexas dentro dela, o modelMapper puro funciona bem aqui!
                                    .map(prod -> modelMapper.map(prod, ProdutoDTO.class))
                                    .collect(Collectors.toCollection(LinkedHashSet::new)));
                        }

                        return subDto;
                    })
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        return dto;
    }


    // Importe sua classe/DTO de Subcategoria
    public List<Subcategoria> listarSubcategoriasPorCategoriaId(Long categoriaId) {
        // Verifica se a categoria existe para evitar erro
        if (!repository.existsById(categoriaId)) {
            throw new RuntimeException("Categoria não encontrada com o ID: " + categoriaId);
        }

        return subCategoriaRepository.findByCategoriaId(categoriaId);
    }

    public Optional<Categoria> buscarPorId(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public Categoria salvar(Categoria categoria) {
        // Aqui você pode adicionar validações, como verificar se o nome já existe
        return repository.save(categoria);
    }

    @Transactional
    public Categoria atualizar(Long id, Categoria novosDados) {
        Categoria categoriaExistente = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada com o ID: " + id));

        categoriaExistente.setNome(novosDados.getNome());
        categoriaExistente.setLoginUsuario(autenticacaoService.getUsername());
        // Se houver subcategorias e você quiser atualizar em lote, a lógica entraria aqui

        return repository.save(categoriaExistente);
    }

    @Transactional
    public void deletar(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Não é possível deletar: Categoria inexistente.");
        }
        repository.deleteById(id);
    }

    // Adicione no seu CategoriaService.java
    public List<CategoriaDTO> listarVitrineRevendedor(String slug) {
        // Busca as categorias cruzadas com a maleta do revendedor
        List<Categoria> categoriasRevendedor = repository.findVitrineDoRevendedor(slug);

        // Converte as entidades para DTO (Use a mesma lógica/mapper que você já usa no listarTodas)
        return categoriasRevendedor.stream()
                .map(categoria -> modelMapper.map(categoria, CategoriaDTO.class))
                .collect(Collectors.toList());
    }
}
