package br.com.claricejoias_ws;

import br.com.claricejoias_ws.config.ModelMapperConfig;
import br.com.claricejoias_ws.dto.CategoriaDTO;
import br.com.claricejoias_ws.model.Categoria;
import br.com.claricejoias_ws.repository.CategoriaRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    @Autowired
    private CategoriaRepository repository;

    @Autowired
    private ModelMapper modelMapper;

    public List<CategoriaDTO> listarTodas() {

        List<Categoria> categorias = repository.findAll();
        return categorias.stream()
                .map(categoria -> modelMapper.map(categoria, CategoriaDTO.class))
                .collect(Collectors.toList());
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
