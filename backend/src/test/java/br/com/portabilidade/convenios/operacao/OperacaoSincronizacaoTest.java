package br.com.portabilidade.convenios.operacao;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.controller.SincronizacaoOperacaoController;
import br.com.portabilidade.convenios.dto.*;
import br.com.portabilidade.convenios.entity.*;
import br.com.portabilidade.convenios.exception.*;
import br.com.portabilidade.convenios.repository.*;
import br.com.portabilidade.convenios.service.SincronizacaoOperacaoService;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperacaoSincronizacaoTest {
    private static final String SCHEMA = "teste_sincronizacao_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.flyway.schemas", () -> SCHEMA);
        registro.add("spring.flyway.default-schema", () -> SCHEMA);
        registro.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }
    @Autowired SincronizacaoOperacaoService service;
    @MockitoSpyBean OperacaoPortabilidadeRepository operacoes;
    @MockitoSpyBean EventoOperacaoRepository eventos;
    @MockitoBean CentralConveniosClient central;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager manager;
    @jakarta.persistence.PersistenceContext EntityManager entityManager;
    MockMvc mvc;
    TransactionTemplate transacao;

    @BeforeEach void preparar() {
        new JdbcTemplate(dataSource).execute("DELETE FROM " + SCHEMA + ".eventos_operacao");
        operacoes.deleteAll();
        transacao = new TransactionTemplate(manager);
        mvc = MockMvcBuilders.standaloneSetup(new SincronizacaoOperacaoController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterAll void limpar() { new JdbcTemplate(dataSource).execute("DROP SCHEMA " + SCHEMA + " CASCADE"); }

    OperacaoPortabilidade criar(StatusOperacao status, boolean protocolo) {
        var operacao = new OperacaoPortabilidade(UUID.randomUUID(), "BEN-1", Set.of("PAR-1"),
                new ContaDestino("001", "1", "2"));
        operacao.setStatusOperacao(status);
        if (protocolo) {
            operacao.setProtocoloCentral(UUID.randomUUID());
            operacao.setStatusIntegracao(StatusIntegracao.ENVIADA);
        }
        return operacoes.saveAndFlush(operacao);
    }
    OperacaoConsultaCentralDTO resposta(UUID protocolo, StatusOperacao status) {
        return new OperacaoConsultaCentralDTO(protocolo, status, OffsetDateTime.now(), null, null);
    }
    void responder(OperacaoPortabilidade operacao, StatusOperacao status) {
        when(central.consultarOperacao(operacao.getProtocoloCentral())).thenAnswer(invocacao -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return resposta(operacao.getProtocoloCentral(), status);
        });
    }
    List<EventoOperacao> historico(UUID id) { return eventos.findByOperacaoIdOrderByDataCriacaoAscIdAsc(id); }

    @ParameterizedTest @EnumSource(value = StatusOperacao.class, names = {"CONFIRMADA", "REJEITADA", "FALHA_TECNICA"})
    void deveAtualizarStatusEAuditoriaAtomicamente(StatusOperacao status) throws Exception {
        var operacao = criar(StatusOperacao.PENDENTE, true);
        responder(operacao, status);
        mvc.perform(post("/api/operacoes/{id}/sincronizar-status", operacao.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(operacao.getId().toString()))
                .andExpect(jsonPath("$.statusOperacao").value(status.name()))
                .andExpect(jsonPath("$.statusIntegracao").value("ENVIADA"));
        var atual = operacoes.findById(operacao.getId()).orElseThrow();
        assertThat(atual.getVersao()).isEqualTo(operacao.getVersao() + 1);
        assertThat(historico(atual.getId())).singleElement().satisfies(evento -> {
            assertThat(evento.getTipo()).isEqualTo(TipoEventoOperacao.STATUS_OPERACAO_ATUALIZADO);
            assertThat(evento.getStatusOperacaoAnterior()).isEqualTo(StatusOperacao.PENDENTE);
            assertThat(evento.getStatusOperacaoNovo()).isEqualTo(status);
        });
        service.sincronizar(operacao.getId());
        assertThat(historico(atual.getId())).hasSize(1);
        verify(central, times(status == StatusOperacao.FALHA_TECNICA ? 2 : 1))
                .consultarOperacao(operacao.getProtocoloCentral());
    }

    @ParameterizedTest @EnumSource(value = StatusOperacao.class, names = {"PENDENTE", "FALHA_TECNICA"})
    void deveManterStatusIgualSemEventoOuAtualizacao(StatusOperacao status) {
        var operacao = criar(status, true);
        responder(operacao, status);
        assertThat(service.sincronizar(operacao.getId()).statusOperacao()).isEqualTo(status);
        assertThat(historico(operacao.getId())).isEmpty();
        var atual = operacoes.findById(operacao.getId()).orElseThrow();
        assertThat(atual.getVersao()).isEqualTo(operacao.getVersao());
        assertThat(atual.getDataAtualizacao()).isEqualTo(operacao.getDataAtualizacao());
    }

    @Test void deveRetornar404E409SemConsultarCentral() throws Exception {
        mvc.perform(post("/api/operacoes/{id}/sincronizar-status", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        var operacao = criar(null, false);
        mvc.perform(post("/api/operacoes/{id}/sincronizar-status", operacao.getId()))
                .andExpect(status().isConflict());
        verifyNoInteractions(central);
        assertThat(operacoes.findById(operacao.getId()).orElseThrow().getStatusOperacao()).isNull();
        assertThat(historico(operacao.getId())).isEmpty();
    }

    @ParameterizedTest @EnumSource(value = StatusOperacao.class, names = {"CONFIRMADA", "REJEITADA"})
    void naoDeveConsultarOperacaoFinalizada(StatusOperacao status) {
        var operacao = criar(status, true);
        assertThat(service.sincronizar(operacao.getId()).statusOperacao()).isEqualTo(status);
        verifyNoInteractions(central);
    }

    @ParameterizedTest @EnumSource(value = StatusOperacao.class, names = {"PENDENTE", "CONFIRMADA", "REJEITADA"})
    void deveRejeitarSaidaDeFalhaTecnicaConformeCentralAtual(StatusOperacao status) throws Exception {
        var operacao = criar(StatusOperacao.FALHA_TECNICA, true);
        responder(operacao, status);
        mvc.perform(post("/api/operacoes/{id}/sincronizar-status", operacao.getId()))
                .andExpect(status().isBadGateway());
        assertThat(operacoes.findById(operacao.getId()).orElseThrow().getStatusOperacao()).isEqualTo(StatusOperacao.FALHA_TECNICA);
        assertThat(historico(operacao.getId())).isEmpty();
    }

    @Test void deveRejeitarCorpoAusenteStatusNuloOuProtocoloDivergente() throws Exception {
        var operacao = criar(StatusOperacao.PENDENTE, true);
        var invalidas = Arrays.asList(null, resposta(operacao.getProtocoloCentral(), null),
                resposta(UUID.randomUUID(), StatusOperacao.CONFIRMADA));
        for (var invalida : invalidas) {
            when(central.consultarOperacao(any())).thenReturn(invalida);
            mvc.perform(post("/api/operacoes/{id}/sincronizar-status", operacao.getId()))
                    .andExpect(status().isBadGateway());
        }
        assertThat(historico(operacao.getId())).isEmpty();
        assertThat(operacoes.findById(operacao.getId()).orElseThrow().getStatusOperacao()).isEqualTo(StatusOperacao.PENDENTE);
    }

    @ParameterizedTest @EnumSource(value = HttpStatus.class, names = {"GATEWAY_TIMEOUT", "SERVICE_UNAVAILABLE", "BAD_GATEWAY"})
    void devePreservarEstadoEmFalhasDeConsulta(HttpStatus status) throws Exception {
        var operacao = criar(StatusOperacao.PENDENTE, true);
        when(central.consultarOperacao(any())).thenThrow(new SincronizacaoException(status, "Falha de consulta", null));
        mvc.perform(post("/api/operacoes/{id}/sincronizar-status", operacao.getId()))
                .andExpect(status().is(status.value()));
        var atual = operacoes.findById(operacao.getId()).orElseThrow();
        assertThat(atual.getStatusOperacao()).isEqualTo(StatusOperacao.PENDENTE);
        assertThat(atual.getVersao()).isEqualTo(operacao.getVersao());
        assertThat(historico(operacao.getId())).isEmpty();
    }

    @Test void deveReverterEstadoEEventoSePersistenciaFalhar() throws Exception {
        var operacao = criar(StatusOperacao.PENDENTE, true);
        responder(operacao, StatusOperacao.CONFIRMADA);
        doAnswer(invocacao -> { entityManager.flush(); throw new IllegalStateException("Falha local"); })
                .when(operacoes).flush();
        mvc.perform(post("/api/operacoes/{id}/sincronizar-status", operacao.getId()))
                .andExpect(status().isInternalServerError());
        assertThat(operacoes.findById(operacao.getId()).orElseThrow().getStatusOperacao()).isEqualTo(StatusOperacao.PENDENTE);
        assertThat(historico(operacao.getId())).isEmpty();
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void deveResolverConcorrenciaComEventoUnico(boolean respostasDivergentes) throws Exception {
        var operacao = criar(StatusOperacao.PENDENTE, true);
        var chamadas = new java.util.concurrent.atomic.AtomicInteger();
        when(central.consultarOperacao(any())).thenAnswer(invocacao -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return resposta(operacao.getProtocoloCentral(), respostasDivergentes && chamadas.incrementAndGet() == 2
                    ? StatusOperacao.REJEITADA : StatusOperacao.CONFIRMADA);
        });
        var barreira = new CyclicBarrier(2);
        doAnswer(invocacao -> {
            EventoOperacao evento = invocacao.getArgument(0);
            entityManager.persist(evento);
            barreira.await(10, TimeUnit.SECONDS);
            return evento;
        }).when(eventos).save(any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> service.sincronizar(operacao.getId()));
            var b = executor.submit(() -> service.sincronizar(operacao.getId()));
            var primeira = a.get(20, TimeUnit.SECONDS);
            var segunda = b.get(20, TimeUnit.SECONDS);
            var oficialLocal = operacoes.findById(operacao.getId()).orElseThrow().getStatusOperacao();
            assertThat(primeira.statusOperacao()).isEqualTo(oficialLocal);
            assertThat(segunda.statusOperacao()).isEqualTo(oficialLocal);
            assertThat(historico(operacao.getId())).singleElement()
                    .satisfies(evento -> assertThat(evento.getStatusOperacaoNovo()).isEqualTo(oficialLocal));
        }
        assertThat(historico(operacao.getId())).hasSize(1);
        assertThat(operacoes.findById(operacao.getId()).orElseThrow().getVersao()).isEqualTo(operacao.getVersao() + 1);
    }

    @ParameterizedTest @EnumSource(value = StatusOperacao.class, names = {"CONFIRMADA", "REJEITADA", "FALHA_TECNICA"})
    void devePreservarEstadoMaisRecenteDuranteConsulta(StatusOperacao concorrente) {
        var operacao = criar(StatusOperacao.PENDENTE, true);
        when(central.consultarOperacao(any())).thenAnswer(invocacao -> {
            transacao.executeWithoutResult(tx -> {
                var atual = operacoes.findById(operacao.getId()).orElseThrow();
                atual.setStatusOperacao(concorrente);
                eventos.save(new EventoOperacao(atual, TipoEventoOperacao.STATUS_OPERACAO_ATUALIZADO,
                        "Transição concorrente simulada", null, null, StatusOperacao.PENDENTE, concorrente, null));
            });
            return resposta(operacao.getProtocoloCentral(), StatusOperacao.PENDENTE);
        });
        if (concorrente == StatusOperacao.FALHA_TECNICA) {
            assertThatThrownBy(() -> service.sincronizar(operacao.getId())).isInstanceOf(ConflitoException.class);
        } else assertThat(service.sincronizar(operacao.getId()).statusOperacao()).isEqualTo(concorrente);
        assertThat(operacoes.findById(operacao.getId()).orElseThrow().getStatusOperacao()).isEqualTo(concorrente);
        assertThat(historico(operacao.getId())).hasSize(1);
    }
}
