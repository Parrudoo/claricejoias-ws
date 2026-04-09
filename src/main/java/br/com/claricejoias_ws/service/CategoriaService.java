package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.CategoriaDTO;
import br.com.claricejoias_ws.model.Categoria;
import br.com.claricejoias_ws.model.Subcategoria;
import br.com.claricejoias_ws.repository.CategoriaRepository;
import br.com.claricejoias_ws.repository.SubCategoriaRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    @Autowired
    private CategoriaRepository repository;

    @Autowired
    private SubCategoriaRepository subCategoriaRepository;

    @Autowired
    private ModelMapper modelMapper;

    public List<CategoriaDTO> listarTodas() {

        List<Categoria> categorias = repository.findAll();
        return categorias.stream()
                .map(categoria -> modelMapper.map(categoria, CategoriaDTO.class))
                .collect(Collectors.toList());
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
}
