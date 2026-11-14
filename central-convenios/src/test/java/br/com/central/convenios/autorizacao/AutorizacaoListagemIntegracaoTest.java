package br.com.central.convenios.autorizacao;

import br.com.central.convenios.BaseCentralConveniosTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class AutorizacaoListagemIntegracaoTest extends BaseCentralConveniosTest {
    @LocalServerPort int porta;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private HttpResponse<String> listar(String parametros) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + porta + "/autorizacoes" + parametros)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void devePaginarNoBancoComOrdemDecrescenteEDesempatePorId() throws Exception {
        // Datas futuras isolam a primeira página dos registros criados pelas demais suítes.
        var ids = new String[]{"00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002",
                "00000000-0000-0000-0000-000000000003"};
        long total = jdbc.queryForObject("select count(*) from autorizacoes", Long.class);
        try {
            for (int i = 0; i < ids.length; i++) {
                jdbc.update("insert into autorizacoes(id, beneficiario_id, convenio_id, data_autorizacao, status) values (?::uuid, ?, 'LISTAGEM', ?::timestamptz, 'ATIVA')",
                        ids[i], "LISTAGEM-" + i, i == 0 ? "2199-01-01T00:00:00Z" : "2199-02-01T00:00:00Z");
            }
            var resposta = listar("?pagina=0&tamanho=2");
            assertThat(resposta.statusCode()).isEqualTo(200);
            var primeira = mapper.readTree(resposta.body());
            assertThat(primeira.path("conteudo").size()).isEqualTo(2);
            assertThat(primeira.path("conteudo").get(0).path("autorizacaoId").asText()).isEqualTo(ids[2]);
            assertThat(primeira.path("conteudo").get(1).path("autorizacaoId").asText()).isEqualTo(ids[1]);
            assertThat(primeira.path("conteudo").get(0).properties()).hasSize(5);
            assertThat(primeira.path("totalElementos").asLong()).isEqualTo(total + 3);
            assertThat(primeira.path("totalPaginas").asInt()).isEqualTo((int) ((total + 4) / 2));
            var segunda = mapper.readTree(listar("?pagina=1&tamanho=2").body());
            assertThat(segunda.path("pagina").asInt()).isEqualTo(1);
            assertThat(segunda.path("tamanho").asInt()).isEqualTo(2);
            assertThat(segunda.path("conteudo").get(0).path("autorizacaoId").asText()).isEqualTo(ids[0]);
            var vazia = mapper.readTree(listar("?pagina=1000000&tamanho=100").body());
            assertThat(vazia.path("conteudo").size()).isZero();
            var padrao = mapper.readTree(listar("").body());
            assertThat(padrao.path("pagina").asInt()).isZero();
            assertThat(padrao.path("tamanho").asInt()).isEqualTo(10);
        } finally {
            jdbc.update("delete from autorizacoes where convenio_id = 'LISTAGEM'");
        }
    }

    @Test
    void deveRejeitarPaginacaoInvalida() throws Exception {
        for (String parametros : new String[]{"pagina=-1", "tamanho=0", "tamanho=101", "pagina=abc"}) {
            assertThat(listar("?" + parametros).statusCode()).isEqualTo(400);
        }
    }
}
