package br.com.portabilidade.convenios.operacao;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.controller.OperacaoController;
import br.com.portabilidade.convenios.dto.*;
import br.com.portabilidade.convenios.entity.*;
import br.com.portabilidade.convenios.exception.*;
import br.com.portabilidade.convenios.repository.OperacaoPortabilidadeRepository;
import br.com.portabilidade.convenios.repository.EventoOperacaoRepository;
import br.com.portabilidade.convenios.service.OperacaoService;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperacaoCriacaoTest {
    private static final String SCHEMA = "teste_criacao_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.flyway.schemas", () -> SCHEMA);
        registro.add("spring.flyway.default-schema", () -> SCHEMA);
        registro.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }
    @Autowired OperacaoService service;
    @Autowired DataSource dataSource;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean OperacaoPortabilidadeRepository operacoes;
    @MockitoBean CentralConveniosClient central;
    @MockitoSpyBean EventoOperacaoRepository eventos;
    MockMvc mvc;
    final OffsetDateTime dataCentral = OffsetDateTime.parse("2026-11-14T12:00:00Z");
    final UUID protocolo = UUID.randomUUID();

    @BeforeEach void preparar() {
        when(central.criarOperacao(any(), any(), any())).thenAnswer(invocacao -> {
            try { return central.criarOperacao(invocacao.getArgument(0), invocacao.getArgument(1)); }
            finally { ((Runnable) invocacao.getArgument(2)).run(); }
        });
        new JdbcTemplate(dataSource).execute("DELETE FROM " + SCHEMA + ".eventos_operacao");
        operacoes.deleteAll();
        mvc = MockMvcBuilders.standaloneSetup(new OperacaoController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterAll void limpar() { new JdbcTemplate(dataSource).execute("DROP SCHEMA " + SCHEMA + " CASCADE"); }

    CriarOperacaoRequestDTO request(String beneficiario) {
        return new CriarOperacaoRequestDTO(beneficiario, List.of("PAR-1", "PAR-2"),
                new CriarOperacaoRequestDTO.ContaDestinoDTO("001", "0001", "123"));
    }
    OperacaoRespostaDTO resposta(StatusOperacao status) {
        return new OperacaoRespostaDTO(protocolo, status, dataCentral, "BEN-1");
    }
    StatusIntegracao statusLocal(UUID chave) {
        return operacoes.findByChaveIdempotencia(chave).orElseThrow().getStatusIntegracao();
    }
    void sucesso(StatusOperacao status) {
        when(central.criarOperacao(any(), any())).thenAnswer(invocacao -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(statusLocal(invocacao.getArgument(1))).isEqualTo(StatusIntegracao.PENDENTE_ENVIO);
            assertThat(service.consultarEventos(operacoes.findByChaveIdempotencia(invocacao.getArgument(1))
                    .orElseThrow().getId())).extracting(EventoOperacaoRespostaDTO::tipo)
                    .containsExactly(TipoEventoOperacao.OPERACAO_CRIADA_LOCALMENTE);
            return ResponseEntity.accepted().body(resposta(status));
        });
    }

    @ParameterizedTest @EnumSource(StatusOperacao.class)
    void devePersistirStatusOficialEReutilizarRespostaCom202(StatusOperacao oficial) {
        sucesso(oficial);
        UUID chave = UUID.randomUUID();
        var primeira = service.criarOperacao(request("BEN-1"), chave);
        var invertida = new CriarOperacaoRequestDTO("BEN-1", List.of("PAR-2", "PAR-1"), request("BEN-1").contaDestino());
        var repetida = service.criarOperacao(invertida, chave);
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(repetida).isEqualTo(primeira);
        assertThat(repetida.getBody()).isEqualTo(resposta(oficial));
        assertThat(statusLocal(chave)).isEqualTo(StatusIntegracao.ENVIADA);
        assertThat(historico(chave)).extracting(EventoOperacaoRespostaDTO::tipo).containsExactly(
                TipoEventoOperacao.OPERACAO_CRIADA_LOCALMENTE, TipoEventoOperacao.TENTATIVA_ENVIO_CENTRAL,
                TipoEventoOperacao.PROTOCOLO_RECEBIDO);
        verify(central, times(1)).criarOperacao(any(), eq(chave));
    }

    @Test void devePreservarContratoHttp202EValidarEntrada() throws Exception {
        sucesso(StatusOperacao.PENDENTE);
        UUID chave = UUID.randomUUID();
        String corpo = """
                {"beneficiarioId":"BEN-1","parcelasIds":["PAR-1","PAR-2"],
                "contaDestino":{"banco":"001","agencia":"0001","conta":"123"}}
                """;
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            mvc.perform(post("/api/operacoes").header("Idempotency-Key", chave)
                    .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.protocolo").value(protocolo.toString()))
                    .andExpect(jsonPath("$.status").value("PENDENTE"));
        }
        mvc.perform(post("/api/operacoes").header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(central, times(1)).criarOperacao(any(), eq(chave));
    }

    @Test void deveRejeitarParcelasDuplicadasSemPersistir() {
        var request = new CriarOperacaoRequestDTO("BEN-1", List.of("PAR-1", "PAR-1"), request("BEN-1").contaDestino());
        assertThatThrownBy(() -> service.criarOperacao(request, UUID.randomUUID()))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThat(operacoes.count()).isZero();
        verifyNoInteractions(central);
    }

    @Test void deveRetornar409ParaConteudoDivergente() throws Exception {
        sucesso(StatusOperacao.PENDENTE);
        UUID chave = UUID.randomUUID();
        service.criarOperacao(request("BEN-1"), chave);
        mvc.perform(post("/api/operacoes").header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"beneficiarioId":"OUTRO","parcelasIds":["PAR-1","PAR-2"],
                "contaDestino":{"banco":"001","agencia":"0001","conta":"123"}}
                """)).andExpect(status().isConflict());
        verify(central, times(1)).criarOperacao(any(), any());
    }

    @Test void deveRegistrar401ComoFalhaERetornar502SemReenviar() throws Exception {
        when(central.criarOperacao(any(), any())).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        UUID chave = UUID.randomUUID();
        mvc.perform(post("/api/operacoes").header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"beneficiarioId":"BEN-1","parcelasIds":["PAR-1","PAR-2"],
                "contaDestino":{"banco":"001","agencia":"0001","conta":"123"}}
                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        "Falha de autenticação na comunicação com a Central de Convênios."));
        assertThat(statusLocal(chave)).isEqualTo(StatusIntegracao.FALHA_ENVIO);
        var historico = historico(chave);
        assertThat(historico).extracting(EventoOperacaoRespostaDTO::tipo).containsExactly(
                TipoEventoOperacao.OPERACAO_CRIADA_LOCALMENTE, TipoEventoOperacao.TENTATIVA_ENVIO_CENTRAL,
                TipoEventoOperacao.FALHA_INTEGRACAO);
        assertThat(historico.get(2).statusIntegracaoAnterior()).isEqualTo(StatusIntegracao.PENDENTE_ENVIO);
        assertThat(historico.get(2).statusIntegracaoNovo()).isEqualTo(StatusIntegracao.FALHA_ENVIO);
        assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), chave))
                .isInstanceOf(CentralIndisponivelException.class).hasMessageContaining("FALHA_ENVIO");
        assertThatThrownBy(() -> service.criarOperacao(request("OUTRO"), chave)).isInstanceOf(ConflitoException.class);
        assertThat(operacoes.count()).isEqualTo(1);
        assertThat(historico(chave)).hasSize(3);
        verify(central, times(1)).criarOperacao(any(), eq(chave));
    }

    @Test void deveTratar408ComoIndeterminado() {
        when(central.criarOperacao(any(), any())).thenThrow(new HttpClientErrorException(HttpStatus.REQUEST_TIMEOUT));
        verificarFalha(StatusIntegracao.ENVIO_INDETERMINADO);
    }

    @Test void devePersistirFalhaConhecidaSemReenviar() {
        when(central.criarOperacao(any(), any())).thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));
        verificarFalha(StatusIntegracao.FALHA_ENVIO);
    }
    @Test void devePersistirTimeoutComoIndeterminadoSemReenviar() {
        when(central.criarOperacao(any(), any())).thenThrow(new CentralIndisponivelException(
                new ResourceAccessException("timeout", new SocketTimeoutException())));
        verificarFalha(StatusIntegracao.ENVIO_INDETERMINADO);
    }
    @Test void deveTratar5xxComoIndeterminado() {
        when(central.criarOperacao(any(), any())).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        verificarFalha(StatusIntegracao.ENVIO_INDETERMINADO);
    }
    @Test void deveTratarCorpoAusenteComoIndeterminado() {
        when(central.criarOperacao(any(), any())).thenReturn(ResponseEntity.accepted().build());
        verificarFalha(StatusIntegracao.ENVIO_INDETERMINADO);
    }
    @Test void deveTratarConexaoRecusadaComoIndeterminado() {
        when(central.criarOperacao(any(), any())).thenThrow(new CentralIndisponivelException(
                new ResourceAccessException("conexão recusada", new java.net.ConnectException())));
        verificarFalha(StatusIntegracao.ENVIO_INDETERMINADO);
    }
    void verificarFalha(StatusIntegracao esperado) {
        UUID chave = UUID.randomUUID();
        assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), chave)).isInstanceOf(CentralIndisponivelException.class);
        assertThat(statusLocal(chave)).isEqualTo(esperado);
        assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), chave)).isInstanceOf(CentralIndisponivelException.class);
        assertThat(operacoes.count()).isEqualTo(1);
        assertThat(historico(chave)).extracting(EventoOperacaoRespostaDTO::tipo).containsExactly(
                TipoEventoOperacao.OPERACAO_CRIADA_LOCALMENTE, TipoEventoOperacao.TENTATIVA_ENVIO_CENTRAL,
                esperado == StatusIntegracao.FALHA_ENVIO ? TipoEventoOperacao.FALHA_INTEGRACAO
                        : TipoEventoOperacao.RESULTADO_INDETERMINADO);
        verify(central, times(1)).criarOperacao(any(), eq(chave));
    }

    @Test void deveManterPendentePreexistenteSemReenvio() {
        UUID chave = UUID.randomUUID();
        operacoes.saveAndFlush(new OperacaoPortabilidade(chave, "BEN-1", Set.of("PAR-1", "PAR-2"), new ContaDestino("001", "0001", "123")));
        assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), chave)).isInstanceOf(CentralIndisponivelException.class);
        assertThat(statusLocal(chave)).isEqualTo(StatusIntegracao.PENDENTE_ENVIO);
        verifyNoInteractions(central);
    }

    @Test void deveResolverInsercoesSimultaneasAposRollback() throws Exception { concorrencia(false); }
    @Test void deveRejeitarPayloadDivergenteEmInsercoesSimultaneas() throws Exception { concorrencia(true); }
    void concorrencia(boolean divergente) throws Exception {
        UUID chave = UUID.randomUUID();
        var barreira = new CyclicBarrier(2);
        var leituras = new AtomicInteger();
        doAnswer(invocacao -> {
            // A query continua real; o spy apenas sincroniza as duas leituras iniciais.
            var resultado = entityManager.createQuery(
                    "select o from OperacaoPortabilidade o where o.chaveIdempotencia = :chave", OperacaoPortabilidade.class)
                    .setParameter("chave", chave).getResultList().stream().findFirst();
            if (leituras.incrementAndGet() <= 2) barreira.await(10, TimeUnit.SECONDS);
            return resultado;
        }).when(operacoes).findByChaveIdempotencia(chave);
        when(central.criarOperacao(any(), eq(chave))).thenAnswer(invocacao -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return ResponseEntity.accepted().body(new OperacaoRespostaDTO(protocolo, StatusOperacao.PENDENTE,
                    dataCentral, ((CriarOperacaoRequestDTO) invocacao.getArgument(0)).beneficiarioId()));
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Object> primeira = () -> executar(request("BEN-1"), chave);
            Callable<Object> segunda = () -> executar(request(divergente ? "BEN-2" : "BEN-1"), chave);
            var a = executor.submit(primeira);
            var b = executor.submit(segunda);
            var resultados = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(ResponseEntity.class::isInstance).count()).withFailMessage("Resultados: %s", resultados).isBetween(1L, divergente ? 1L : 2L);
            for (var resultado : resultados) {
                if (!(resultado instanceof ResponseEntity)) assertThat(resultado)
                        .isInstanceOf(divergente ? ConflitoException.class : CentralIndisponivelException.class);
            }
        }
        assertThat(operacoes.count()).isEqualTo(1);
        assertThat(statusLocal(chave)).isEqualTo(StatusIntegracao.ENVIADA);
        assertThat(historico(chave)).extracting(EventoOperacaoRespostaDTO::tipo).containsExactly(
                TipoEventoOperacao.OPERACAO_CRIADA_LOCALMENTE, TipoEventoOperacao.TENTATIVA_ENVIO_CENTRAL,
                TipoEventoOperacao.PROTOCOLO_RECEBIDO);
        verify(central, times(1)).criarOperacao(any(), eq(chave));
    }
    List<EventoOperacaoRespostaDTO> historico(UUID chave) {
        return service.consultarEventos(operacoes.findByChaveIdempotencia(chave).orElseThrow().getId());
    }

    @Test void deveConsultarHistoricoPeloIdLocalEOrdenarEmpates() throws Exception {
        sucesso(StatusOperacao.CONFIRMADA);
        UUID chave = UUID.randomUUID();
        service.criarOperacao(request("BEN-1"), chave);
        UUID id = operacoes.findByChaveIdempotencia(chave).orElseThrow().getId();
        var protocoloRecebido = historico(chave).get(2);
        assertThat(protocoloRecebido.statusIntegracaoAnterior()).isEqualTo(StatusIntegracao.PENDENTE_ENVIO);
        assertThat(protocoloRecebido.statusIntegracaoNovo()).isEqualTo(StatusIntegracao.ENVIADA);
        assertThat(protocoloRecebido.statusOperacaoNovo()).isEqualTo(StatusOperacao.CONFIRMADA);
        assertThat(protocoloRecebido.protocoloCentral()).isEqualTo(protocolo);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/operacoes/{id}/eventos", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("OPERACAO_CRIADA_LOCALMENTE"))
                .andExpect(jsonPath("$[2].protocoloCentral").value(protocolo.toString()))
                .andExpect(jsonPath("$[0].operacao").doesNotExist());
        new JdbcTemplate(dataSource).update("UPDATE " + SCHEMA
                + ".eventos_operacao SET data_criacao = '2026-01-01T00:00:00Z' WHERE operacao_id = ?", id);
        var esperados = new JdbcTemplate(dataSource).queryForList("SELECT id FROM " + SCHEMA
                + ".eventos_operacao WHERE operacao_id = ? ORDER BY id", UUID.class, id);
        assertThat(historico(chave)).extracting(EventoOperacaoRespostaDTO::id).containsExactlyElementsOf(esperados);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/operacoes/{id}/eventos", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test void deveConsultarOperacaoPreexistenteSemInventarEventos() throws Exception {
        var operacao = operacoes.saveAndFlush(new OperacaoPortabilidade(UUID.randomUUID(), "BEN-1",
                Set.of("PAR-1"), new ContaDestino("001", "1", "1")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/operacoes/{id}/eventos", operacao.getId())).andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test void deveReverterCriacaoQuandoEventoNaoPodeSerPersistido() {
        doThrow(new IllegalStateException("Falha local simulada")).when(eventos).save(any());
        assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(operacoes.count()).isZero();
        verifyNoInteractions(central);
    }

    @Test void deveRegistrarIndeterminadoSePersistenciaDoProtocoloFalhar() {
        sucesso(StatusOperacao.PENDENTE);
        falharEvento(TipoEventoOperacao.PROTOCOLO_RECEBIDO);
        UUID chave = UUID.randomUUID();
        assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), chave))
                .isInstanceOf(CentralIndisponivelException.class);
        var operacao = operacoes.findByChaveIdempotencia(chave).orElseThrow();
        assertThat(operacao.getStatusIntegracao()).isEqualTo(StatusIntegracao.ENVIO_INDETERMINADO);
        assertThat(operacao.getProtocoloCentral()).isNull();
        assertThat(operacao.getStatusOperacao()).isNull();
        assertThat(historico(chave)).extracting(EventoOperacaoRespostaDTO::tipo).containsExactly(
                TipoEventoOperacao.OPERACAO_CRIADA_LOCALMENTE, TipoEventoOperacao.TENTATIVA_ENVIO_CENTRAL,
                TipoEventoOperacao.RESULTADO_INDETERMINADO);
        assertThat(historico(chave).get(2).descricao()).contains("persistência local");
    }

    @Test void devePreservarTentativaQuandoEventoDeFalhaNaoPersiste() {
        when(central.criarOperacao(any(), any())).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
        falharEvento(TipoEventoOperacao.FALHA_INTEGRACAO);
        UUID chave = UUID.randomUUID();
        assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), chave))
                .isInstanceOf(IllegalStateException.class);
        assertThat(statusLocal(chave)).isEqualTo(StatusIntegracao.PENDENTE_ENVIO);
        assertThat(historico(chave)).extracting(EventoOperacaoRespostaDTO::tipo)
                .containsExactly(TipoEventoOperacao.OPERACAO_CRIADA_LOCALMENTE, TipoEventoOperacao.TENTATIVA_ENVIO_CENTRAL);
    }

    @Test void deveMarcarIndeterminadoQuandoAuditoriaDaTentativaFalhar() {
        sucesso(StatusOperacao.PENDENTE);
        falharEvento(TipoEventoOperacao.TENTATIVA_ENVIO_CENTRAL);
        UUID chave = UUID.randomUUID();
        var erro = catchThrowable(() -> service.criarOperacao(request("BEN-1"), chave));
        assertThat(erro).isInstanceOf(CentralIndisponivelException.class);

        assertThat(statusLocal(chave)).isEqualTo(StatusIntegracao.ENVIO_INDETERMINADO);
        assertThat(historico(chave)).extracting(EventoOperacaoRespostaDTO::tipo)
                .containsExactly(TipoEventoOperacao.OPERACAO_CRIADA_LOCALMENTE, TipoEventoOperacao.RESULTADO_INDETERMINADO);
        verify(central, times(1)).criarOperacao(any(), eq(chave));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"503-sucesso", "timeout-sucesso", "503-falha", "502-falha", "504-falha", "conexao-falha", "401", "409", "interrupcao"})
    void deveExecutarRetryRealComAuditoriaIndependente(String cenario) throws Exception {
        var builder = org.springframework.web.client.RestClient.builder();
        var servidor = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var mapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        var esperas = new java.util.ArrayList<Long>();
        var client = new CentralConveniosClient(builder.build(), mapper, "chave-teste") {
            @Override protected void aguardarBackoff(long milissegundos) throws InterruptedException {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                esperas.add(milissegundos);
                if (cenario.equals("interrupcao")) throw new InterruptedException("teste");
            }
        };
        UUID chave = UUID.randomUUID();
        String json = mapper.writeValueAsString(request("BEN-1"));
        String assinatura = br.com.portabilidade.convenios.seguranca.AssinaturaHmac.assinar(json, "chave-teste");
        int quantidade = cenario.endsWith("-falha") || cenario.equals("503-sucesso") ? 3
                : cenario.equals("timeout-sucesso") ? 2 : 1;
        for (int indice = 0; indice < quantidade; indice++) {
            int enviosAnteriores = indice;
            var expectativa = servidor.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                    .andExpect(requisicao -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                        assertThat(operacoes.count()).isEqualTo(1);
                        assertThat(historico(chave).stream().filter(evento -> evento.tipo()
                                == TipoEventoOperacao.TENTATIVA_ENVIO_CENTRAL).count()).isEqualTo(enviosAnteriores);
                    })
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Idempotency-Key", chave.toString()))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("X-Assinatura-HMAC", assinatura))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(json));
            if (cenario.endsWith("sucesso") && indice == quantidade - 1) {
                expectativa.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(resposta(StatusOperacao.PENDENTE))));
            } else if (cenario.equals("conexao-falha")) {
                expectativa.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withException(new java.net.ConnectException()));
            } else if (cenario.startsWith("timeout")) {
                expectativa.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withException(new SocketTimeoutException()));
            } else {
                expectativa.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(
                        cenario.equals("401") ? HttpStatus.UNAUTHORIZED : cenario.equals("409") ? HttpStatus.CONFLICT
                                : cenario.startsWith("502") ? HttpStatus.BAD_GATEWAY
                                : cenario.startsWith("504") ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE));
            }
        }
        doAnswer(invocacao -> client.criarOperacao(
                invocacao.getArgument(0), invocacao.getArgument(1), invocacao.getArgument(2)))
                .when(central).criarOperacao(any(), any(), any());
        try {
            if (cenario.endsWith("sucesso")) {
                assertThat(service.criarOperacao(request("BEN-1"), chave).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                assertThat(statusLocal(chave)).isEqualTo(StatusIntegracao.ENVIADA);
            } else {
                assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), chave))
                        .isInstanceOf(cenario.equals("401") ? AutenticacaoCentralException.class : CentralIndisponivelException.class);
                assertThat(statusLocal(chave)).isEqualTo(cenario.equals("401") || cenario.equals("409")
                        ? StatusIntegracao.FALHA_ENVIO : StatusIntegracao.ENVIO_INDETERMINADO);
            }
            assertThat(historico(chave).stream().filter(evento -> evento.tipo() == TipoEventoOperacao.TENTATIVA_ENVIO_CENTRAL).count())
                    .isEqualTo(quantidade);
            assertThat(operacoes.count()).isEqualTo(1);
            if (quantidade == 3) assertThat(esperas).containsExactly(1000L, 2000L);
            if (cenario.equals("timeout-sucesso")) assertThat(esperas).containsExactly(1000L);
            if (cenario.equals("interrupcao")) assertThat(Thread.currentThread().isInterrupted()).isTrue();
            servidor.verify();
        } finally { Thread.interrupted(); }
    }

    void falharEvento(TipoEventoOperacao tipo) {
        doAnswer(invocacao -> {
            EventoOperacao evento = invocacao.getArgument(0);
            entityManager.persist(evento);
            entityManager.flush();
            if (evento.getTipo() == tipo) throw new IllegalStateException("Falha local simulada");
            return evento;
        }).when(eventos).save(any());
    }

    Object executar(CriarOperacaoRequestDTO request, UUID chave) {
        try { return service.criarOperacao(request, chave); }
        catch (RuntimeException excecao) { return excecao; }
    }
}
