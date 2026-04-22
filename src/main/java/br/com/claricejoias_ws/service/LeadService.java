package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.LeadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LeadService {

    @Autowired
    private LeadRepository repository;

    public Lead salvar(Lead lead) {
        return repository.save(lead);
    }

    public List<Lead> listarTodos() {
        return repository.findAll();
    }

    public Lead alternarStatus(Long id) {
        return repository.findById(id).map(lead -> {
            lead.setAtivo(!lead.getAtivo()); // Inverte o status atual
            return repository.save(lead);
        }).orElseThrow(() -> new RuntimeException("Lead não encontrado com o ID: " + id));
    }

    public Lead marcarComoComprado(Long id) {
        return repository.findById(id).map(lead -> {
            lead.setComprou(true);
            return repository.save(lead);
        }).orElseThrow(() -> new RuntimeException("Lead não encontrado com o ID: " + id));
    }
}