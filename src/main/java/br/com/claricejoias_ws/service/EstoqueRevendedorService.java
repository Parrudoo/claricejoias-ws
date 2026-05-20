package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.EstoqueRevendedorDTO;
import br.com.claricejoias_ws.dto.TransferenciaEstoqueDTO;
import br.com.claricejoias_ws.model.*;
import br.com.claricejoias_ws.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EstoqueRevendedorService {


    private final ProdutoRepository produtoRepository;
    private final RevendedorRepository revendedorRepository;
    private final ModelMapper modelMapper;
    private final EstoqueRevendedorRepository estoqueRevendedorRepository;

    @Transactional
    public void transferirParaMaleta(TransferenciaEstoqueDTO dto) {
        // 1. Validar Produto e Revendedor
        Produto produto = produtoRepository.findById(dto.getProdutoId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));

        Revendedor revendedor = revendedorRepository.findById(dto.getRevendedorId())
                .orElseThrow(() -> new RuntimeException("Revendedor não encontrado."));

        // 2. Baixar do Estoque Central
        produto.diminuirEstoqueCentral(dto.getQuantidade());
        produtoRepository.save(produto);

        // 3. Adicionar na Maleta do Revendedor
        EstoqueRevendedor estoque = estoqueRevendedorRepository
                .findByProdutoIdAndRevendedorId(produto.getId(), revendedor.getId())
                .orElse(new EstoqueRevendedor());

        if (estoque.getId() == null) {
            estoque.setProduto(produto);
            estoque.setRevendedor(revendedor);
            estoque.setQuantidade(0);
        }

        estoque.setQuantidade(estoque.getQuantidade() + dto.getQuantidade());
        estoqueRevendedorRepository.save(estoque);
    }

    public List<EstoqueRevendedorDTO> listarEstoquePorRevendedor(String revendedorId) {

        List<EstoqueRevendedorDTO> estoqueRevendedors = estoqueRevendedorRepository.
                findByRevendedorId(revendedorId).stream().map(estoque -> modelMapper.map(estoque, EstoqueRevendedorDTO.class)).toList();
        return estoqueRevendedors;
    }

    @Transactional
    public void devolverParaEstoqueCentral(TransferenciaEstoqueDTO dto) {

        // 1. Validação básica
        if (dto.getQuantidade() <= 0) {
            throw new IllegalArgumentException("A quantidade para devolução deve ser maior que zero.");
        }

        // 2. Buscar o registro do produto na maleta do revendedor
        // (Ajuste o nome do método de busca conforme estiver no seu EstoqueRevendedorRepository)
        EstoqueRevendedor estoqueMaleta = estoqueRevendedorRepository
                .findByRevendedorIdAndProdutoId(dto.getRevendedorId(), dto.getProdutoId())
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado na maleta deste revendedor."));

        // 3. Garantir que o revendedor não está tentando devolver mais do que ele realmente tem
        if (estoqueMaleta.getQuantidade() < dto.getQuantidade()) {
            throw new IllegalArgumentException("Quantidade a devolver (" + dto.getQuantidade() +
                    ") é maior do que a disponível na maleta (" + estoqueMaleta.getQuantidade() + ").");
        }

        // 4. Buscar o produto no estoque central da loja
        Produto produtoCentral = produtoRepository.findById(dto.getProdutoId())
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado no estoque central."));

        // 5. Fazer a matemática da transferência (Caminho inverso)
        // Retira da maleta
        estoqueMaleta.setQuantidade(estoqueMaleta.getQuantidade() - dto.getQuantidade());

        // Devolve para a matriz
        produtoCentral.setQuantidadeEstoqueCentral(produtoCentral.getQuantidadeEstoqueCentral() + dto.getQuantidade());

        // 6. Persistir as alterações no banco de dados
        produtoRepository.save(produtoCentral);

        // Se a maleta do revendedor ficar sem nenhuma unidade deste produto,
        // é uma boa prática excluir o registro para manter a tabela limpa e otimizada.
        if (estoqueMaleta.getQuantidade() == 0) {
            estoqueRevendedorRepository.delete(estoqueMaleta);
        } else {
            estoqueRevendedorRepository.save(estoqueMaleta);
        }
    }
}