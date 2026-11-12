package br.com.central.convenios.autorizacao;

import br.com.central.convenios.BaseCentralConveniosTest;
import br.com.central.convenios.repository.AutorizacaoRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;

class AutorizacaoIntegracaoTest extends BaseCentralConveniosTest {

    @LocalServerPort
    private int porta;

    @Autowired
    private AutorizacaoRepository repository;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> registrar(String corpo) throws Exception {
        var requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/autorizacoes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
        return cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    private String corpo(String beneficiario) {
        return "{\"beneficiarioId\":\"" + beneficiario + "\",\"convenioId\":\"CONV-001\"}";
    }

    @Test
    void deveCriarAutorizacaoAtivaComDadosGeradosPeloServidor() throws Exception {
        String beneficiario = UUID.randomUUID().toString();
        var resposta = registrar(corpo(beneficiario));
        assertThat(resposta.statusCode()).isEqualTo(201);
        var persistida = repository.findByBeneficiarioIdAndConvenioId(beneficiario, "CONV-001").orElseThrow();
        assertThat(resposta.body()).contains("\"autorizacaoId\":\"" + persistida.getId() + "\"",
                "\"beneficiarioId\":\"" + beneficiario + "\"", "\"convenioId\":\"CONV-001\"",
                "\"status\":\"ATIVA\"", "\"dataAutorizacao\":");
        assertThat(persistida.getDataAutorizacao()).isNotNull();
    }

    @Test
    void deveRetornarMesmaAutorizacaoNoReenvio() throws Exception {
        String corpo = corpo(UUID.randomUUID().toString());
        var primeira = registrar(corpo);
        var segunda = registrar(corpo);
        assertThat(primeira.statusCode()).isEqualTo(201);
        assertThat(segunda.statusCode()).isEqualTo(200);
        assertThat(segunda.body()).isEqualTo(primeira.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"beneficiarioId\":\"BEN-001\"}",
            "{\"convenioId\":\"CONV-001\"}",
            "{\"beneficiarioId\":null,\"convenioId\":\"CONV-001\"}",
            "{\"beneficiarioId\":\" \",\"convenioId\":\"CONV-001\"}",
            "{\"beneficiarioId\":\"BEN-001\",\"convenioId\":\"\"}", "{", ""})
    void deveRejeitarCamposAusentesVaziosEJsonInvalido(String corpo) throws Exception {
        long quantidade = repository.count();
        assertThat(registrar(corpo).statusCode()).isEqualTo(400);
        assertThat(repository.count()).isEqualTo(quantidade);
    }

    @Test
    void deveRejeitarIdentificadoresAcimaDoLimite() throws Exception {
        assertThat(registrar(corpo("B".repeat(101))).statusCode()).isEqualTo(400);
        assertThat(registrar("{\"beneficiarioId\":\"BEN\",\"convenioId\":\"" + "C".repeat(101) + "\"}")
                .statusCode()).isEqualTo(400);
    }

    @Test
    void devePersistirSomenteUmaAutorizacaoEmRequisicoesConcorrentes() throws Exception {
        String beneficiario = UUID.randomUUID().toString();
        int tentativas = 8;
        var prontas = new CountDownLatch(tentativas);
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tentativas)) {
            var tarefas = new ArrayList<Future<HttpResponse<String>>>();
            for (int i = 0; i < tentativas; i++) {
                tarefas.add(executor.submit(() -> {
                    prontas.countDown();
                    if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo de espera excedido");
                    return registrar(corpo(beneficiario));
                }));
            }
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            inicio.countDown();
            var respostas = new ArrayList<HttpResponse<String>>();
            for (var tarefa : tarefas) respostas.add(tarefa.get(30, TimeUnit.SECONDS));
            assertThat(respostas.stream().filter(r -> r.statusCode() == 201).count()).isEqualTo(1);
            assertThat(respostas.stream().filter(r -> r.statusCode() == 200).count()).isEqualTo(tentativas - 1);
            assertThat(respostas.stream().map(HttpResponse::body).distinct().count()).isEqualTo(1);
            assertThat(repository.findAll().stream().filter(a -> a.getBeneficiarioId().equals(beneficiario)).count())
                    .isEqualTo(1);
        } finally {
            inicio.countDown();
        }
    }
}
