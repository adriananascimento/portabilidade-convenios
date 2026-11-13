package br.com.central.convenios.agenda;

import br.com.central.convenios.BaseCentralConveniosTest;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Import(AgendaIntegracaoTest.ConfiguracaoTemporal.class)
class AgendaIntegracaoTest extends BaseCentralConveniosTest {
    private static final LocalDate HOJE = LocalDate.of(2040, 6, 15);

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracaoTemporal {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2040-06-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @LocalServerPort
    private int porta;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;

    private JsonNode consultar(String beneficiario) throws Exception {
        var requisicao = HttpRequest.newBuilder(
                URI.create("http://localhost:" + porta + "/beneficiarios/" + beneficiario + "/agenda"))
                .GET().build();
        var resposta = HttpClient.newHttpClient().send(requisicao, HttpResponse.BodyHandlers.ofString());
        assertThat(resposta.statusCode()).isEqualTo(200);
        var corpo = mapper.readTree(resposta.body());
        assertThat(corpo.get("beneficiarioId").asText()).isEqualTo(beneficiario);
        assertThat(corpo.size()).isEqualTo(2);
        assertThat(corpo.get("parcelas").isArray()).isTrue();
        return corpo.get("parcelas");
    }

    private void autorizar(String beneficiario, String convenio, String status) {
        jdbc.update("""
                INSERT INTO autorizacoes (id, beneficiario_id, convenio_id, data_autorizacao, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, UUID.randomUUID(), beneficiario, convenio, status);
    }

    private String parcela(String beneficiario, String convenio, LocalDate data) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO parcelas (id, beneficiario_id, convenio_id, valor, data_desconto)
                VALUES (?, ?, ?, ?, ?)
                """, id, beneficiario, convenio, new BigDecimal("350.00"), data);
        return id;
    }

    @Test
    void deveRetornarSomenteParcelasAtuaisEFuturasDosConveniosAutorizados() throws Exception {
        String beneficiario = UUID.randomUUID().toString();
        autorizar(beneficiario, "CONV-001", "ATIVA");
        autorizar(beneficiario, "CONV-002", "REVOGADA");
        String atual = parcela(beneficiario, "CONV-001", HOJE);
        String futura = parcela(beneficiario, "CONV-001", HOJE.plusDays(1));
        parcela(beneficiario, "CONV-001", HOJE.minusDays(1));
        parcela(beneficiario, "CONV-002", HOJE.plusDays(1));
        parcela(beneficiario, "CONV-003", HOJE.plusDays(1));
        String outro = UUID.randomUUID().toString();
        autorizar(outro, "CONV-001", "ATIVA");
        parcela(outro, "CONV-001", HOJE.plusDays(1));

        var parcelas = consultar(beneficiario);
        assertThat(parcelas.size()).isEqualTo(2);
        assertThat(parcelas.get(0).get("parcelaId").asText()).isEqualTo(atual);
        assertThat(parcelas.get(1).get("parcelaId").asText()).isEqualTo(futura);
        var primeira = parcelas.get(0);
        assertThat(primeira.size()).isEqualTo(4);
        assertThat(primeira.get("convenioId").asText()).isEqualTo("CONV-001");
        assertThat(primeira.get("valor").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(primeira.get("dataDesconto").asText()).isEqualTo(HOJE.toString());
    }

    @Test
    void deveRetornarListaVaziaSemAutorizacaoAtiva() throws Exception {
        String beneficiario = UUID.randomUUID().toString();
        autorizar(beneficiario, "CONV-001", "REVOGADA");
        parcela(beneficiario, "CONV-001", HOJE.plusDays(1));
        parcela(beneficiario, "CONV-002", HOJE.plusDays(1));
        String outro = UUID.randomUUID().toString();
        autorizar(outro, "CONV-002", "ATIVA");
        assertThat(consultar(beneficiario).isEmpty()).isTrue();
    }

    @Test
    void deveOrdenarParcelasPorDataCrescenteEntreConvenios() throws Exception {
        String beneficiario = UUID.randomUUID().toString();
        autorizar(beneficiario, "CONV-001", "ATIVA");
        autorizar(beneficiario, "CONV-002", "ATIVA");
        String ultima = parcela(beneficiario, "CONV-001", HOJE.plusDays(30));
        String primeira = parcela(beneficiario, "CONV-002", HOJE.plusDays(1));
        String segunda = parcela(beneficiario, "CONV-001", HOJE.plusDays(10));
        var parcelas = consultar(beneficiario);
        assertThat(parcelas.size()).isEqualTo(3);
        assertThat(parcelas.get(0).get("parcelaId").asText()).isEqualTo(primeira);
        assertThat(parcelas.get(1).get("parcelaId").asText()).isEqualTo(segunda);
        assertThat(parcelas.get(2).get("parcelaId").asText()).isEqualTo(ultima);
    }

    @Test
    void deveRetornarListaVaziaComAutorizacaoMasSemParcelasElegiveis() throws Exception {
        String beneficiario = UUID.randomUUID().toString();
        autorizar(beneficiario, "CONV-001", "ATIVA");
        assertThat(consultar(beneficiario).isEmpty()).isTrue();
        parcela(beneficiario, "CONV-001", HOJE.minusDays(1));
        assertThat(consultar(beneficiario).isEmpty()).isTrue();
        assertThat(consultar(UUID.randomUUID().toString()).isEmpty()).isTrue();
    }
}
