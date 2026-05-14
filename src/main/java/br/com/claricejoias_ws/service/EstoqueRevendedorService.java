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
}