package br.com.central.convenios.service;

import br.com.central.convenios.dto.AgendaRespostaDTO;
import br.com.central.convenios.dto.ParcelaRespostaDTO;
import br.com.central.convenios.entity.StatusAutorizacao;
import br.com.central.convenios.repository.ParcelaRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgendaService {
    private final ParcelaRepository repository;
    private final Clock relogio;

    public AgendaService(ParcelaRepository repository, Clock relogio) {
        this.repository = repository;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public AgendaRespostaDTO consultar(String beneficiarioId) {
        var parcelas = repository.consultarAgenda(beneficiarioId, LocalDate.now(relogio), StatusAutorizacao.ATIVA);
        return new AgendaRespostaDTO(beneficiarioId, parcelas.stream().map(ParcelaRespostaDTO::de).toList());
    }
}
