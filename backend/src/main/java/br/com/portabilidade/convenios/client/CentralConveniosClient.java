package br.com.portabilidade.convenios.client;

import br.com.portabilidade.convenios.dto.*;
import br.com.portabilidade.convenios.exception.CentralIndisponivelException;
import br.com.portabilidade.convenios.exception.SincronizacaoException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class CentralConveniosClient {

    private final RestClient restClient;

    public CentralConveniosClient(@Qualifier("centralRestClient") RestClient centralRestClient) {
        this.restClient = centralRestClient;
    }

    public OperacaoConsultaCentralDTO consultarOperacao(UUID protocolo) {
        try {
            return restClient.get().uri("/operacoes-portabilidade/{protocolo}", protocolo)
                    .retrieve().onStatus(status -> !status.is2xxSuccessful(), (requisicao, resposta) -> {
                        throw new RestClientResponseException("Resposta HTTP inesperada da Central",
                                resposta.getStatusCode().value(), "", resposta.getHeaders(), null,
                                java.nio.charset.StandardCharsets.UTF_8);
                    }).body(OperacaoConsultaCentralDTO.class);
        } catch (RestClientResponseException excecao) {
            var status = excecao.getStatusCode();
            if (status.value() == 404) throw falhaConsulta(HttpStatus.BAD_GATEWAY,
                    "Protocolo não encontrado na Central", excecao);
            if (status.value() == 408 || status.value() == 504) throw falhaConsulta(
                    HttpStatus.GATEWAY_TIMEOUT, "Tempo limite na consulta à Central", excecao);
            throw falhaConsulta(status.is5xxServerError() ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_GATEWAY, "Falha HTTP na consulta à Central", excecao);
        } catch (ResourceAccessException excecao) {
            for (Throwable causa = excecao; causa != null; causa = causa.getCause()) {
                if (causa instanceof java.net.SocketTimeoutException) throw falhaConsulta(
                        HttpStatus.GATEWAY_TIMEOUT, "Tempo limite na consulta à Central", excecao);
            }
            throw falhaConsulta(HttpStatus.SERVICE_UNAVAILABLE,
                    "Central indisponível para consulta", excecao);
        } catch (RestClientException excecao) {
            throw falhaConsulta(HttpStatus.BAD_GATEWAY,
                    "Resposta inesperada da Central", excecao);
        }
    }

    private SincronizacaoException falhaConsulta(
            HttpStatus status, String mensagem, Throwable causa) {
        return new SincronizacaoException(status, mensagem, causa);
    }

    public AutorizacoesPaginaDTO listarAutorizacoes(int pagina, int tamanho) {
        try {
            var resposta = restClient.get()
                    .uri(uri -> uri.path("/autorizacoes").queryParam("pagina", pagina)
                            .queryParam("tamanho", tamanho).build())
                    .retrieve()
                    .body(AutorizacoesPaginaDTO.class);
            if (resposta == null) throw new CentralIndisponivelException(
                    new IllegalStateException("Resposta vazia da Central"));
            return resposta;
        } catch (RestClientException excecao) {
            throw new CentralIndisponivelException(excecao);
        }
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

    public ResponseEntity<AutorizacaoRespostaDTO> criarAutorizacao(CriarAutorizacaoRequestDTO request) {
        try {
            return restClient.post()
                    .uri("/autorizacoes")
                    .body(request)
                    .retrieve()
                    .toEntity(AutorizacaoRespostaDTO.class);

        } catch (ResourceAccessException excecao) {
            throw new CentralIndisponivelException(excecao);
        }
    }

    public ResponseEntity<OperacaoRespostaDTO> criarOperacao(CriarOperacaoRequestDTO request, UUID chaveIdempotencia) {
        try {
            return restClient.post()
                    .uri("/operacoes-portabilidade")
                    .header("Idempotency-Key", chaveIdempotencia.toString())
                    .body(request)
                    .retrieve()
                    .toEntity(OperacaoRespostaDTO.class);

        } catch (ResourceAccessException excecao) {
            throw new CentralIndisponivelException(excecao);
        }
    }

}
