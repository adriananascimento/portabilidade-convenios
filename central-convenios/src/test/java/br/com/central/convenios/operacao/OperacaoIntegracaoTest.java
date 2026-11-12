package br.com.central.convenios.operacao;

import br.com.central.convenios.BaseCentralConveniosTest;
import br.com.central.convenios.service.ProcessamentoOperacaoService;
import java.time.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(OperacaoIntegracaoTest.ConfiguracaoTemporal.class)
class OperacaoIntegracaoTest extends BaseCentralConveniosTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2040-06-15T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracaoTemporal {
        @Bean @Primary
        Clock relogioOperacoes() {
            return Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC);
        }
    }

    @Autowired ProcessamentoOperacaoService processamento;
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
    private HttpResponse<String> consultar(UUID protocolo) throws Exception {
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta
                + "/operacoes-portabilidade/" + protocolo)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void deveConsultarOperacaoPendenteSemAlterarEstado() throws Exception {
        var beneficiario = preparar("ATIVA");
        var criada = enviar(UUID.randomUUID().toString(), corpo(beneficiario));
        var protocolo = UUID.fromString(mapper.readTree(criada.body()).get("protocolo").asText());
        for (int i = 0; i < 2; i++) {
            var resposta = consultar(protocolo);
            assertThat(resposta.statusCode()).isEqualTo(200);
            var json = mapper.readTree(resposta.body());
            assertThat(json.size()).isEqualTo(5);
            assertThat(json.get("protocolo").asText()).isEqualTo(protocolo.toString());
            assertThat(json.get("status").asText()).isEqualTo("PENDENTE");
            assertThat(json.get("dataCriacao").asText()).isNotBlank();
            assertThat(json.get("dataProcessamento").isNull()).isTrue();
            assertThat(json.get("motivoRejeicao").isNull()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM operacoes_portabilidade WHERE protocolo = ?",
                String.class, protocolo)).isEqualTo("PENDENTE");
    }

    @Test
    void deveRetornar404ParaProtocoloInexistente() throws Exception {
        assertThat(consultar(UUID.randomUUID()).statusCode()).isEqualTo(404);
    }

    @Test
    void deveConsultarOperacoesFinalizadas() throws Exception {
        for (String status : List.of("CONFIRMADA", "REJEITADA")) {
            var beneficiario = preparar("ATIVA");
            var criada = enviar(UUID.randomUUID().toString(), corpo(beneficiario));
            var protocolo = UUID.fromString(mapper.readTree(criada.body()).get("protocolo").asText());
            String motivo = status.equals("REJEITADA") ? "Autorização inativa" : null;
            jdbc.update("UPDATE operacoes_portabilidade SET status = ?, data_processamento = CURRENT_TIMESTAMP, "
                    + "motivo_rejeicao = ? WHERE protocolo = ?", status, motivo, protocolo);
            var resposta = consultar(protocolo);
            assertThat(resposta.statusCode()).isEqualTo(200);
            var json = mapper.readTree(resposta.body());
            assertThat(json.get("status").asText()).isEqualTo(status);
            assertThat(json.get("dataProcessamento").asText()).isNotBlank();
            if (motivo == null) assertThat(json.get("motivoRejeicao").isNull()).isTrue();
            else assertThat(json.get("motivoRejeicao").asText()).isEqualTo(motivo);
        }
    }

    private UUID registrar(String chave, String beneficiario) throws Exception {
        var resposta = enviar(chave, corpo(beneficiario));
        assertThat(resposta.statusCode()).isEqualTo(202);
        return UUID.fromString(mapper.readTree(resposta.body()).get("protocolo").asText());
    }

    private void aguardarElegibilidade(UUID protocolo) {
        // Avança o instante persistido de criação, sem esperar o agendador ou tempo real.
        jdbc.update("UPDATE operacoes_portabilidade SET data_criacao = ? WHERE protocolo = ?",
                AGORA.minusSeconds(10), protocolo);
    }

    private int reservas(UUID protocolo) {
        return jdbc.queryForObject("SELECT count(*) FROM reservas_parcelas WHERE operacao_protocolo = ?",
                Integer.class, protocolo);
    }

    private int parcelasNaAgenda(String beneficiario) throws Exception {
        var resposta = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta
                + "/beneficiarios/" + beneficiario + "/agenda")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(resposta.statusCode()).isEqualTo(200);
        return mapper.readTree(resposta.body()).get("parcelas").size();
    }

    @Test
    void deveRespeitarAtrasoEConfirmarPeloProcessamentoDePendentes() throws Exception {
        var beneficiario = preparar("ATIVA");
        var chave = UUID.randomUUID().toString();
        assertThat(parcelasNaAgenda(beneficiario)).isEqualTo(2);
        var protocolo = registrar(chave, beneficiario);
        assertThat(reservas(protocolo)).isEqualTo(2);
        assertThat(parcelasNaAgenda(beneficiario)).isZero();
        assertThat(processamento.processar(protocolo)).isFalse();
        processamento.processarPendentes();
        assertThat(mapper.readTree(consultar(protocolo).body()).get("status").asText()).isEqualTo("PENDENTE");
        aguardarElegibilidade(protocolo);
        processamento.processarPendentes();
        var json = mapper.readTree(consultar(protocolo).body());
        assertThat(json.get("status").asText()).isEqualTo("CONFIRMADA");
        assertThat(OffsetDateTime.parse(json.get("dataProcessamento").asText())).isEqualTo(AGORA);
        assertThat(json.get("motivoRejeicao").isNull()).isTrue();
        assertThat(reservas(protocolo)).isEqualTo(2);
        assertThat(parcelasNaAgenda(beneficiario)).isZero();
        assertThat(enviar(UUID.randomUUID().toString(), corpo(beneficiario)).statusCode()).isEqualTo(409);
        var repetida = enviar(chave, corpo(beneficiario));
        assertThat(repetida.statusCode()).isEqualTo(202);
        assertThat(mapper.readTree(repetida.body()).get("protocolo").asText()).isEqualTo(protocolo.toString());
        assertThat(mapper.readTree(repetida.body()).get("status").asText()).isEqualTo("CONFIRMADA");
        jdbc.update("UPDATE autorizacoes SET status = 'REVOGADA' WHERE beneficiario_id = ?", beneficiario);
        assertThat(processamento.processar(protocolo)).isFalse();
        assertThat(mapper.readTree(consultar(protocolo).body())).isEqualTo(json);
        assertThat(reservas(protocolo)).isEqualTo(2);
        assertThat(quantidade(beneficiario)).isEqualTo(1);
    }

    @Test
    void deveRejeitarLiberarReservasEPreservarHistoricoEIdempotencia() throws Exception {
        var beneficiario = preparar("ATIVA");
        var chave = UUID.randomUUID().toString();
        var protocolo = registrar(chave, beneficiario);
        aguardarElegibilidade(protocolo);
        jdbc.update("UPDATE autorizacoes SET status = 'REVOGADA' WHERE beneficiario_id = ?", beneficiario);
        assertThat(processamento.processar(protocolo)).isTrue();
        var rejeitada = consultar(protocolo).body();
        var json = mapper.readTree(rejeitada);
        assertThat(json.get("status").asText()).isEqualTo("REJEITADA");
        assertThat(OffsetDateTime.parse(json.get("dataProcessamento").asText())).isEqualTo(AGORA);
        assertThat(json.get("motivoRejeicao").asText()).isEqualTo("Beneficiário sem autorização ativa");
        assertThat(reservas(protocolo)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operacoes_parcelas WHERE operacao_protocolo = ?",
                Integer.class, protocolo)).isEqualTo(2);
        assertThat(parcelasNaAgenda(beneficiario)).isZero();
        jdbc.update("UPDATE autorizacoes SET status = 'ATIVA' WHERE beneficiario_id = ?", beneficiario);
        assertThat(parcelasNaAgenda(beneficiario)).isEqualTo(2);
        assertThat(processamento.processar(protocolo)).isFalse();
        assertThat(consultar(protocolo).body()).isEqualTo(rejeitada);
        var repetida = enviar(chave, corpo(beneficiario));
        assertThat(repetida.statusCode()).isEqualTo(202);
        assertThat(mapper.readTree(repetida.body()).get("status").asText()).isEqualTo("REJEITADA");
        assertThat(mapper.readTree(repetida.body()).get("protocolo").asText()).isEqualTo(protocolo.toString());
        assertThat(reservas(protocolo)).isZero();
        var nova = registrar(UUID.randomUUID().toString(), beneficiario);
        assertThat(nova).isNotEqualTo(protocolo);
        assertThat(reservas(nova)).isEqualTo(2);
        assertThat(parcelasNaAgenda(beneficiario)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operacoes_parcelas WHERE parcela_id = ?",
                Integer.class, beneficiario + "-1")).isEqualTo(2);
        assertThat(enviar(chave, corpo(beneficiario, List.of(beneficiario + "-1"), "outra")).statusCode()).isEqualTo(409);
    }

    @Test
    void deveRejeitarSeParcelaVencerAntesDoProcessamento() throws Exception {
        var beneficiario = preparar("ATIVA");
        var protocolo = registrar(UUID.randomUUID().toString(), beneficiario);
        aguardarElegibilidade(protocolo);
        jdbc.update("UPDATE parcelas SET data_desconto = ? WHERE id = ?", AGORA.toLocalDate().minusDays(1), beneficiario + "-1");
        assertThat(processamento.processar(protocolo)).isTrue();
        var json = mapper.readTree(consultar(protocolo).body());
        assertThat(json.get("status").asText()).isEqualTo("REJEITADA");
        assertThat(json.get("motivoRejeicao").asText()).isEqualTo("Parcela indisponível para portabilidade");
        assertThat(reservas(protocolo)).isZero();
        assertThat(parcelasNaAgenda(beneficiario)).isEqualTo(1);
    }

    @Test
    void deveProcessarUmaUnicaVezSobConcorrencia() throws Exception {
        var beneficiario = preparar("ATIVA");
        var protocolo = registrar(UUID.randomUUID().toString(), beneficiario);
        aguardarElegibilidade(protocolo);
        var barreira = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> tarefa = () -> { barreira.await(10, TimeUnit.SECONDS); return processamento.processar(protocolo); };
            var primeira = executor.submit(tarefa);
            var segunda = executor.submit(tarefa);
            assertThat(List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(mapper.readTree(consultar(protocolo).body()).get("status").asText()).isEqualTo("CONFIRMADA");
        assertThat(reservas(protocolo)).isEqualTo(2);
    }

    @Test
    void deveDesfazerLiberacaoEFinalizacaoSeTransacaoFalhar() throws Exception {
        var beneficiario = preparar("ATIVA");
        var protocolo = registrar(UUID.randomUUID().toString(), beneficiario);
        aguardarElegibilidade(protocolo);
        jdbc.update("UPDATE autorizacoes SET status = 'REVOGADA' WHERE beneficiario_id = ?", beneficiario);
        // O trigger simula uma falha real no banco, depois de DELETE das reservas e antes do commit.
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION falhar_finalizacao_teste() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.beneficiario_id = '%s' THEN
                        RAISE EXCEPTION 'Falha simulada no processamento';
                    END IF;
                    RETURN NEW;
                END $$
                """.formatted(beneficiario));
        jdbc.execute("CREATE CONSTRAINT TRIGGER falha_processamento_teste AFTER UPDATE ON operacoes_portabilidade "
                + "DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION falhar_finalizacao_teste()");
        try {
            assertThatThrownBy(() -> processamento.processar(protocolo)).isInstanceOf(RuntimeException.class);
            var json = mapper.readTree(consultar(protocolo).body());
            assertThat(json.get("status").asText()).isEqualTo("PENDENTE");
            assertThat(json.get("dataProcessamento").isNull()).isTrue();
            assertThat(json.get("motivoRejeicao").isNull()).isTrue();
            assertThat(reservas(protocolo)).isEqualTo(2);
        } finally {
            jdbc.execute("DROP TRIGGER falha_processamento_teste ON operacoes_portabilidade");
            jdbc.execute("DROP FUNCTION falhar_finalizacao_teste()");
        }
        assertThat(processamento.processar(protocolo)).isTrue();
        assertThat(reservas(protocolo)).isZero();
    }

}
