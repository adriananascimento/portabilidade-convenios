package br.com.portabilidade.convenios.client;

import br.com.portabilidade.convenios.dto.AgendaRespostaDTO;
import br.com.portabilidade.convenios.exception.CentralIndisponivelException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class CentralConveniosClient {

    private final RestClient restClient;

    public CentralConveniosClient(@Qualifier("centralRestClient") RestClient centralRestClient) {
        this.restClient = centralRestClient;
    }

    public AgendaRespostaDTO consultarAgenda(String beneficiarioId) {
        try {
            return restClient.get()
                    .uri("/beneficiarios/{beneficiarioId}/agenda", beneficiarioId)
                    .retrieve()
                    .body(AgendaRespostaDTO.class);

        } catch (ResourceAccessException excecao) {
            throw new CentralIndisponivelException(excecao);
        }
    }
}
