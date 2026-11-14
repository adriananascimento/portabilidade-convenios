package br.com.portabilidade.convenios.operacao;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.controller.OperacaoController;
import br.com.portabilidade.convenios.entity.*;
import br.com.portabilidade.convenios.repository.OperacaoPortabilidadeRepository;
import br.com.portabilidade.convenios.service.OperacaoService;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperacaoListagemTest {
    private static final String SCHEMA = "teste_listagem_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void configurarSchema(DynamicPropertyRegistry registro) {
        registro.add("spring.flyway.schemas", () -> SCHEMA);
        registro.add("spring.flyway.default-schema", () -> SCHEMA);
        registro.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @Autowired private OperacaoPortabilidadeRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    private CentralConveniosClient central;
    private OperacaoService service;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        central = mock(CentralConveniosClient.class);
        service = new OperacaoService(central, repository, transactionManager);
        mvc = MockMvcBuilders.standaloneSetup(new OperacaoController(service)).build();
    }

    @AfterEach
    void verificarAusenciaDeConsultaRemota() {
        verifyNoInteractions(central);
    }

    @AfterAll
    void removerSchema() {
        new JdbcTemplate(dataSource).execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void devePaginarComOrdenacaoPorDataEIdDecrescentes() throws Exception {
        // IDs conhecidos evitam depender da comparação de UUIDs com sinal em Java.
        // Sem parcelas persistidas para permitir trocar a PK da massa abaixo.
        inserirSemParcelas("00000000-0000-0000-0000-000000000003", "2026-11-12T12:00:00Z");
        inserirSemParcelas("00000000-0000-0000-0000-000000000001", "2026-11-14T12:00:00Z");
        inserirSemParcelas("00000000-0000-0000-0000-000000000002", "2026-11-14T12:00:00Z");
        mvc.perform(get("/api/operacoes").param("pagina", "0").param("tamanho", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(2)).andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2)).andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.conteudo[0].id").value("00000000-0000-0000-0000-000000000002"))
                .andExpect(jsonPath("$.conteudo[1].id").value("00000000-0000-0000-0000-000000000001"));
        assertThat(service.listar(1, 2).conteudo()).extracting(item -> item.id().toString())
                .containsExactly("00000000-0000-0000-0000-000000000003");
        var fora = service.listar(2, 2);
        assertThat(fora.conteudo()).isEmpty();
        assertThat(fora.totalElementos()).isEqualTo(3);
    }

    private OperacaoPortabilidade inserirSemParcelas(String id, String data) {
        var operacao = repository.saveAndFlush(new OperacaoPortabilidade(UUID.randomUUID(), "BEN-1",
                Set.of(), new ContaDestino("001", "1", "2")));
        entityManager.createNativeQuery("UPDATE " + SCHEMA + ".operacoes_portabilidade "
                + "SET id = :novoId, data_criacao = cast(:data AS timestamptz) WHERE id = :id")
                .setParameter("novoId", UUID.fromString(id)).setParameter("data", data)
                .setParameter("id", operacao.getId()).executeUpdate();
        entityManager.clear();
        return repository.findById(UUID.fromString(id)).orElseThrow();
    }

    @Test
    void deveRetornarListagemVaziaComParametrosPadrao() throws Exception {
        mvc.perform(get("/api/operacoes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").isEmpty()).andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(10)).andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void deveExporSomenteCamposAprovadosMesmoSemProtocolo() throws Exception {
        var operacao = inserirSemParcelas("00000000-0000-0000-0000-000000000001", "2026-11-14T12:00:00Z");
        mvc.perform(get("/api/operacoes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].length()").value(6))
                .andExpect(jsonPath("$.conteudo[0].beneficiarioId").value("BEN-1"))
                .andExpect(jsonPath("$.conteudo[0].dataCriacao").value("2026-11-14T12:00:00Z"))
                .andExpect(jsonPath("$.conteudo[0].statusIntegracao").value("PENDENTE_ENVIO"))
                .andExpect(jsonPath("$.conteudo[0].statusOperacao").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.conteudo[0].protocoloCentral").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.conteudo[0].protocolo").doesNotExist())
                .andExpect(jsonPath("$.conteudo[0].status").doesNotExist());
        UUID protocolo = UUID.randomUUID();
        operacao.setProtocoloCentral(protocolo);
        operacao.setStatusIntegracao(StatusIntegracao.ENVIADA);
        operacao.setStatusOperacao(StatusOperacao.CONFIRMADA);
        operacao.setDataCriacaoCentral(java.time.OffsetDateTime.parse("2026-11-14T13:00:00Z"));
        repository.flush();
        entityManager.clear();
        var item = service.listar(0, 10).conteudo().getFirst();
        assertThat(item.protocoloCentral()).isEqualTo(protocolo);
        assertThat(item.statusIntegracao()).isEqualTo(StatusIntegracao.ENVIADA);
        assertThat(item.statusOperacao()).isEqualTo(StatusOperacao.CONFIRMADA);
        assertThat(item.dataCriacao()).isEqualTo(java.time.OffsetDateTime.parse("2026-11-14T12:00:00Z"));
    }
}
