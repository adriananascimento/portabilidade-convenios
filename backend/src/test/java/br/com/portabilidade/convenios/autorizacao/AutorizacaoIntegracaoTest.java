package br.com.portabilidade.convenios.autorizacao;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.controller.AutorizacaoController;
import br.com.portabilidade.convenios.exception.GlobalExceptionHandler;
import br.com.portabilidade.convenios.service.AutorizacaoService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AutorizacaoIntegracaoTest {
    private HttpServer central;
    private MockMvc mvc;
    private String consulta;
    private int status = 200;

    @BeforeEach
    void preparar() throws Exception {
        central = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        central.createContext("/autorizacoes", exchange -> {
            consulta = exchange.getRequestURI().getQuery();
            byte[] corpo = """
                    {"conteudo":[{"autorizacaoId":"550e8400-e29b-41d4-a716-446655440000",
                    "beneficiarioId":"BEN-001","convenioId":"CONV-001",
                    "dataAutorizacao":"2026-11-13T12:00:00Z","status":"ATIVA"}],
                    "pagina":2,"tamanho":1,"totalElementos":4,"totalPaginas":4}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Interno", "nao-repassar");
            exchange.sendResponseHeaders(status, corpo.length);
            exchange.getResponseBody().write(corpo);
            exchange.close();
        });
        central.start();
        var client = new CentralConveniosClient(RestClient.builder()
                .baseUrl("http://localhost:" + central.getAddress().getPort()).build());
        mvc = MockMvcBuilders.standaloneSetup(new AutorizacaoController(new AutorizacaoService(client)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void encerrar() { central.stop(0); }

    @Test
    void devePreservarPaginacaoSemRepassarHeadersDaCentral() throws Exception {
        mvc.perform(get("/api/autorizacoes?pagina=2&tamanho=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.tamanho").value(1)).andExpect(jsonPath("$.totalElementos").value(4))
                .andExpect(jsonPath("$.totalPaginas").value(4))
                .andExpect(jsonPath("$.conteudo[0].beneficiarioId").value("BEN-001"))
                .andExpect(header().doesNotExist("X-Interno"));
        assertThat(consulta).isEqualTo("pagina=2&tamanho=1");
    }

    @Test
    void deveTratarFalhaHttpDaCentral() throws Exception {
        status = 503;
        mvc.perform(get("/api/autorizacoes")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Não foi possível comunicar com a Central de Convênios."));
    }

    @Test
    void deveTratarFalhaDeConexao() throws Exception {
        central.stop(0);
        mvc.perform(get("/api/autorizacoes")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void deveRejeitarPaginacaoInvalidaSemConsultarCentral() throws Exception {
        for (String parametros : new String[]{"pagina=-1", "tamanho=0", "tamanho=101", "pagina=abc"}) {
            mvc.perform(get("/api/autorizacoes?" + parametros)).andExpect(status().isBadRequest());
        }
        assertThat(consulta).isNull();
    }
}
