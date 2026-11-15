package br.com.portabilidade.convenios.operacao;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.dto.CriarOperacaoRequestDTO;
import br.com.portabilidade.convenios.entity.StatusOperacao;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;

class OperacaoClientTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {401, 422, 408, 500})
    void devePreservarStatusHttpParaClassificacaoNoService(int status) {
        var builder = RestClient.builder();
        var servidor = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        servidor.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(
                        org.springframework.http.HttpStatusCode.valueOf(status)));
        var client = new CentralConveniosClient(builder.build(),
                tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(), "chave-teste");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.criarOperacao(
                new CriarOperacaoRequestDTO("BEN-1", List.of("PAR-1"),
                        new CriarOperacaoRequestDTO.ContaDestinoDTO("001", "1", "2")), UUID.randomUUID()))
                .isInstanceOfSatisfying(org.springframework.web.client.RestClientResponseException.class,
                        erro -> assertThat(erro.getStatusCode().value()).isEqualTo(status));
        servidor.verify();
    }

    @Test void deveEnviarMesmaChaveEPayloadEReceber202() throws Exception {
        var chaveRecebida = new AtomicReference<String>();
        var corpoRecebido = new AtomicReference<String>();
        var servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        UUID protocolo = UUID.randomUUID();
        servidor.createContext("/operacoes-portabilidade", exchange -> {
            chaveRecebida.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            corpoRecebido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resposta = ("{\"protocolo\":\"" + protocolo + "\",\"status\":\"FALHA_TECNICA\","
                    + "\"dataCriacao\":\"2026-11-14T12:00:00Z\",\"beneficiarioId\":\"BEN-1\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(202, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });
        servidor.start();
        try {
            var client = new CentralConveniosClient(RestClient.builder()
                    .baseUrl("http://localhost:" + servidor.getAddress().getPort()).build(), tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(), "chave-teste");
            UUID chave = UUID.randomUUID();
            var resposta = client.criarOperacao(new CriarOperacaoRequestDTO("BEN-1", List.of("PAR-1"),
                    new CriarOperacaoRequestDTO.ContaDestinoDTO("001", "1", "2")), chave);
            assertThat(chaveRecebida.get()).isEqualTo(chave.toString());
            assertThat(corpoRecebido.get()).contains("\"beneficiarioId\":\"BEN-1\"", "\"parcelasIds\":[\"PAR-1\"]", "\"banco\":\"001\"");
            assertThat(resposta.getStatusCode().value()).isEqualTo(202);
            assertThat(resposta.getBody().protocolo()).isEqualTo(protocolo);
            assertThat(resposta.getBody().status()).isEqualTo(StatusOperacao.FALHA_TECNICA);
        } finally { servidor.stop(0); }
    }
}
