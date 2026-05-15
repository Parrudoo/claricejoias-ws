package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.model.Revendedor;
import br.com.claricejoias_ws.repository.RevendedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RevendedorService {

    private final RevendedorRepository revendedorRepository;

    public Optional<Revendedor> findById(String usuarioId){
       return revendedorRepository.findById(usuarioId);
    }
}
