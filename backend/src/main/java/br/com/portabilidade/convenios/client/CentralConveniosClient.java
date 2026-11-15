package br.com.portabilidade.convenios.client;

import br.com.portabilidade.convenios.dto.*;
import br.com.portabilidade.convenios.exception.CentralIndisponivelException;
import br.com.portabilidade.convenios.exception.SincronizacaoException;
import br.com.portabilidade.convenios.seguranca.AssinaturaHmac;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
public class CentralConveniosClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String chaveHmac;

    public CentralConveniosClient(
            @Qualifier("centralRestClient") RestClient centralRestClient,
            JsonMapper jsonMapper,
            @Value("${central.integracao.chave-hmac}") String chaveHmac
    ) {
        this.restClient = centralRestClient;
        this.jsonMapper = jsonMapper;
        this.chaveHmac = chaveHmac;
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
        return criarOperacao(request, chaveIdempotencia, () -> {});
    }

    public ResponseEntity<OperacaoRespostaDTO> criarOperacao(
            CriarOperacaoRequestDTO request, UUID chaveIdempotencia, Runnable registrarTentativa) {
        final String json;
        final String assinatura;
        try {
            json = jsonMapper.writeValueAsString(request);
            assinatura = AssinaturaHmac.assinar(json, chaveHmac);
        } catch (Exception excecao) {
            throw new CentralIndisponivelException(excecao);
        }
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CentralIndisponivelException(new InterruptedException("Envio interrompido"));
            }
            try {
                try {
                    return restClient.post()
                            .uri("/operacoes-portabilidade")
                            .header("Idempotency-Key", chaveIdempotencia.toString())
                            .header("X-Assinatura-HMAC", assinatura)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(json)
                            .retrieve()
                            .toEntity(OperacaoRespostaDTO.class);
                } finally {
                    // O envio já foi iniciado; persistir inclusive quando o transporte falhar.
                    registrarTentativa.run();
                }
            } catch (RestClientException excecao) {
                boolean transitoria = falhaTransitoriaDeTransporte(excecao)
                        || excecao instanceof RestClientResponseException resposta
                        && (resposta.getStatusCode().value() == 502
                        || resposta.getStatusCode().value() == 503
                        || resposta.getStatusCode().value() == 504);
                if (!transitoria || tentativa == 3) throw excecao;
                try {
                    aguardarBackoff(1000L << (tentativa - 1));
                } catch (InterruptedException interrupcao) {
                    Thread.currentThread().interrupt();
                    throw new CentralIndisponivelException(interrupcao);
                }
            }
        }
        throw new IllegalStateException("Limite de tentativas excedido");
    }

    private boolean falhaTransitoriaDeTransporte(RestClientException excecao) {
        if (!(excecao instanceof ResourceAccessException) || Thread.currentThread().isInterrupted()) return false;
        for (Throwable causa = excecao.getCause(); causa != null; causa = causa.getCause()) {
            if (causa instanceof java.net.SocketTimeoutException
                    || causa instanceof java.net.SocketException
                    || causa instanceof java.net.UnknownHostException
                    || causa instanceof java.net.http.HttpTimeoutException) return true;
        }
        return false;
    }

    protected void aguardarBackoff(long milissegundos) throws InterruptedException {
        Thread.sleep(milissegundos);
    }

}
