package br.com.portabilidade.convenios.service;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.dto.AgendaRespostaDTO;
import org.springframework.stereotype.Service;

@Service
public class AgendaService {

    private final CentralConveniosClient centralClient;

    public AgendaService(CentralConveniosClient centralClient) {
        this.centralClient = centralClient;
    }

    public AgendaRespostaDTO consultarAgenda(String beneficiarioId) {
        return centralClient.consultarAgenda(beneficiarioId);
    }
}
