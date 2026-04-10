package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.model.Lead;
import br.com.claricejoias_ws.repository.LeadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
}