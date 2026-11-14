package br.com.portabilidade.convenios.operacao;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.config.RestClientConfig;
import br.com.portabilidade.convenios.entity.StatusOperacao;
import br.com.portabilidade.convenios.exception.SincronizacaoException;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OperacaoConsultaClientTest {
    @Test void deveConsultarPorProtocolo() {
        var builder = RestClient.builder();
        var servidor = MockRestServiceServer.bindTo(builder).build();
        UUID protocolo = UUID.randomUUID();
        servidor.expect(requestTo("/operacoes-portabilidade/" + protocolo)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"protocolo\":\"" + protocolo + "\",\"status\":\"CONFIRMADA\"}", MediaType.APPLICATION_JSON));
        var resposta = new CentralConveniosClient(builder.build()).consultarOperacao(protocolo);
        assertThat(resposta.protocolo()).isEqualTo(protocolo);
        assertThat(resposta.status()).isEqualTo(StatusOperacao.CONFIRMADA);
        servidor.verify();
    }

    @ParameterizedTest @CsvSource({"404,502", "408,504", "504,504", "500,503", "503,503", "400,502", "302,502"})
    void deveClassificarFalhasHttp(int remoto, int esperado) {
        var builder = RestClient.builder();
        var servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(remoto)));
        assertThatThrownBy(() -> new CentralConveniosClient(builder.build()).consultarOperacao(UUID.randomUUID()))
                .isInstanceOfSatisfying(SincronizacaoException.class, erro -> assertThat(erro.getStatus().value()).isEqualTo(esperado));
        servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"{", "{\"status\":\"DESCONHECIDO\"}"})
    void deveRejeitarJsonOuStatusInvalido(String corpo) {
        var builder = RestClient.builder();
        var servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(anything()).andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new CentralConveniosClient(builder.build()).consultarOperacao(UUID.randomUUID()))
                .isInstanceOfSatisfying(SincronizacaoException.class,
                        erro -> assertThat(erro.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        servidor.verify();
    }

    @Test void deveClassificarConexaoRecusada() throws Exception {
        int porta;
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { porta = socket.getLocalPort(); }
        var client = new CentralConveniosClient(new RestClientConfig().centralRestClient(RestClient.builder(),
                "http://localhost:" + porta));
        assertThatThrownBy(() -> client.consultarOperacao(UUID.randomUUID()))
                .isInstanceOfSatisfying(SincronizacaoException.class,
                        erro -> assertThat(erro.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test void deveAplicarTimeoutDeLeituraReal() throws Exception {
        var liberar = new CountDownLatch(1);
        var servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/operacoes-portabilidade", exchange -> {
            try { liberar.await(12, TimeUnit.SECONDS); }
            catch (InterruptedException erro) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        servidor.start();
        try {
            var client = new CentralConveniosClient(new RestClientConfig().centralRestClient(RestClient.builder(),
                    "http://localhost:" + servidor.getAddress().getPort()));
            long inicio = System.nanoTime();
            assertThatThrownBy(() -> client.consultarOperacao(UUID.randomUUID()))
                    .isInstanceOfSatisfying(SincronizacaoException.class,
                            erro -> assertThat(erro.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio)).isBetween(4500L, 10000L);
        } finally { liberar.countDown(); servidor.stop(0); }
    }

    @Test void deveConectarFactoryComAmbosTimeoutsAoCliente() {
        var builder = mock(RestClient.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(mock(RestClient.class));
        new RestClientConfig().centralRestClient(builder, "http://central");
        var factory = ArgumentCaptor.forClass(ClientHttpRequestFactory.class);
        verify(builder).requestFactory(factory.capture());
        assertThat(ReflectionTestUtils.getField(factory.getValue(), "connectTimeout")).isEqualTo(3000);
        assertThat(ReflectionTestUtils.getField(factory.getValue(), "readTimeout")).isEqualTo(5000);
    }
}
