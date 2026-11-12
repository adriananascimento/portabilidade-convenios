package br.com.central.convenios.operacao;

import br.com.central.convenios.BaseCentralConveniosTest;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OperacaoIntegracaoTest extends BaseCentralConveniosTest {
    @LocalServerPort int porta;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private final HttpClient cliente = HttpClient.newHttpClient();

    private String preparar(String autorizacao) {
        String beneficiario = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO autorizacoes VALUES (?, ?, 'CONV', CURRENT_TIMESTAMP, ?)",
                UUID.randomUUID(), beneficiario, autorizacao);
        for (int i = 1; i <= 2; i++) {
            jdbc.update("INSERT INTO parcelas VALUES (?, ?, 'CONV', 100.25, DATE '2099-01-01', 'DISPONIVEL')",
                    beneficiario + "-" + i, beneficiario);
        }
        return beneficiario;
    }

    private String corpo(String beneficiario, List<String> parcelas, String conta) {
        return mapper.writeValueAsString(Map.of("beneficiarioId", beneficiario, "parcelasIds", parcelas,
                "contaDestino", Map.of("banco", "001", "agencia", "1234", "conta", conta)));
    }

    private String corpo(String beneficiario) {
        return corpo(beneficiario, List.of(beneficiario + "-1", beneficiario + "-2"), "123456-7");
    }

    private HttpResponse<String> enviar(String chave, String corpo) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/operacoes-portabilidade"))
                .header("Content-Type", "application/json");
        if (chave != null) builder.header("Idempotency-Key", chave);
        return cliente.send(builder.POST(HttpRequest.BodyPublishers.ofString(corpo)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private int quantidade(String beneficiario) {
        return jdbc.queryForObject("SELECT count(*) FROM operacoes_portabilidade WHERE beneficiario_id = ?",
                Integer.class, beneficiario);
    }

    @Test
    void deveCriarOperacaoComVinculosEConta() throws Exception {
        var beneficiario = preparar("ATIVA");
        var chave = UUID.randomUUID().toString();
        var resposta = enviar(chave, corpo(beneficiario));
        assertThat(resposta.statusCode()).isEqualTo(202);
        var json = mapper.readTree(resposta.body());
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("status").asText()).isEqualTo("PENDENTE");
        var protocolo = UUID.fromString(json.get("protocolo").asText());
        var registro = jdbc.queryForMap("SELECT * FROM operacoes_portabilidade WHERE protocolo = ?", protocolo);
        assertThat(registro).containsEntry("chave_idempotencia", UUID.fromString(chave))
                .containsEntry("beneficiario_id", beneficiario).containsEntry("banco", "001")
                .containsEntry("agencia", "1234").containsEntry("conta", "123456-7")
                .containsEntry("status", "PENDENTE");
        assertThat(registro.get("data_criacao")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operacoes_parcelas WHERE operacao_protocolo = ?",
                Integer.class, protocolo)).isEqualTo(2);
    }

    @Test
    void deveRejeitarInexistentesVencidasEOutroBeneficiarioSemPersistir() throws Exception {
        var beneficiario = preparar("ATIVA");
        assertThat(enviar(UUID.randomUUID().toString(), corpo(beneficiario,
                List.of(beneficiario + "-1", "inexistente"), "conta")).statusCode()).isEqualTo(422);
        jdbc.update("UPDATE parcelas SET data_desconto = DATE '2020-01-01' WHERE id = ?", beneficiario + "-1");
        assertThat(enviar(UUID.randomUUID().toString(), corpo(beneficiario)).statusCode()).isEqualTo(409);
        var outro = preparar("ATIVA");
        assertThat(enviar(UUID.randomUUID().toString(), corpo(beneficiario,
                List.of(outro + "-1"), "conta")).statusCode()).isEqualTo(409);
        assertThat(quantidade(beneficiario)).isZero();
    }

    @Test
    void deveExigirAutorizacaoAtivaParaTodosConvenios() throws Exception {
        for (String status : List.of("REVOGADA", "ATIVA")) {
            var beneficiario = preparar(status);
            if (status.equals("ATIVA")) {
                jdbc.update("UPDATE parcelas SET convenio_id = 'SEM-AUTORIZACAO' WHERE id = ?", beneficiario + "-2");
            }
            assertThat(enviar(UUID.randomUUID().toString(), corpo(beneficiario)).statusCode()).isEqualTo(422);
            assertThat(quantidade(beneficiario)).isZero();
        }
    }

    @Test
    void deveValidarListaCamposEHeader() throws Exception {
        var beneficiario = preparar("ATIVA");
        for (var parcelas : List.of(List.<String>of(), List.of("x", "x"), List.of(" "))) {
            assertThat(enviar(UUID.randomUUID().toString(), corpo(beneficiario, parcelas, "conta"))
                    .statusCode()).isEqualTo(400);
        }
        for (var corpo : List.of("{}", "{", corpo(beneficiario, List.of("x"), " "),
                "{\"beneficiarioId\":\"BEN\",\"parcelasIds\":[\"x\"],\"contaDestino\":null}")) {
            assertThat(enviar(UUID.randomUUID().toString(), corpo).statusCode()).isEqualTo(400);
        }
        assertThat(enviar(null, corpo(beneficiario)).statusCode()).isEqualTo(400);
        assertThat(enviar("invalida", corpo(beneficiario)).statusCode()).isEqualTo(400);
        assertThat(quantidade(beneficiario)).isZero();
    }

    @Test
    void deveReenviarMesmoConteudoInclusiveComOrdemDiferente() throws Exception {
        var beneficiario = preparar("ATIVA");
        var chave = UUID.randomUUID().toString();
        var primeira = enviar(chave, corpo(beneficiario));
        var repetida = enviar(chave, corpo(beneficiario,
                List.of(beneficiario + "-2", beneficiario + "-1"), "123456-7"));
        assertThat(primeira.statusCode()).isEqualTo(202);
        assertThat(repetida.statusCode()).isEqualTo(202);
        assertThat(repetida.body()).isEqualTo(primeira.body());
        assertThat(quantidade(beneficiario)).isEqualTo(1);
    }

    @Test
    void deveRejeitarChaveComConteudoDiferente() throws Exception {
        var beneficiario = preparar("ATIVA");
        var chave = UUID.randomUUID().toString();
        assertThat(enviar(chave, corpo(beneficiario)).statusCode()).isEqualTo(202);
        assertThat(enviar(chave, corpo(beneficiario, List.of(beneficiario + "-1"), "123456-7"))
                .statusCode()).isEqualTo(409);
        assertThat(enviar(chave, corpo(beneficiario, List.of(beneficiario + "-1", beneficiario + "-2"), "outra"))
                .statusCode()).isEqualTo(409);
        assertThat(quantidade(beneficiario)).isEqualTo(1);
    }

    private List<HttpResponse<String>> concorrentes(String primeiraChave, String segundaChave,
                                                    String primeiroCorpo, String segundoCorpo) throws Exception {
        var barreira = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> { barreira.await(10, TimeUnit.SECONDS); return enviar(primeiraChave, primeiroCorpo); });
            var segunda = executor.submit(() -> { barreira.await(10, TimeUnit.SECONDS); return enviar(segundaChave, segundoCorpo); });
            return List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS));
        }
    }

    @Test
    void deveResolverMesmaChaveConcorrente() throws Exception {
        var beneficiario = preparar("ATIVA");
        var chave = UUID.randomUUID().toString();
        var respostas = concorrentes(chave, chave, corpo(beneficiario), corpo(beneficiario));
        assertThat(respostas).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(202));
        assertThat(respostas.get(0).body()).isEqualTo(respostas.get(1).body());
        assertThat(quantidade(beneficiario)).isEqualTo(1);
    }

    @Test
    void deveRejeitarConteudoDiferenteComMesmaChaveConcorrente() throws Exception {
        var beneficiario = preparar("ATIVA");
        var chave = UUID.randomUUID().toString();
        var respostas = concorrentes(chave, chave, corpo(beneficiario),
                corpo(beneficiario, List.of(beneficiario + "-1"), "outra"));
        assertThat(respostas.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(202, 409);
        assertThat(quantidade(beneficiario)).isEqualTo(1);
    }

    @Test
    void deveReservarParcelaApenasUmaVezComChavesConcorrentesDiferentes() throws Exception {
        var beneficiario = preparar("ATIVA");
        var respostas = concorrentes(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                corpo(beneficiario), corpo(beneficiario, List.of(beneficiario + "-1"), "outra"));
        assertThat(respostas.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(202, 409);
        assertThat(quantidade(beneficiario)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operacoes_parcelas WHERE parcela_id = ?",
                Integer.class, beneficiario + "-1")).isEqualTo(1);
    }
}
