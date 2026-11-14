package br.com.portabilidade.convenios.operacao;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.controller.OperacaoController;
import br.com.portabilidade.convenios.dto.*;
import br.com.portabilidade.convenios.entity.*;
import br.com.portabilidade.convenios.exception.*;
import br.com.portabilidade.convenios.repository.OperacaoPortabilidadeRepository;
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
    MockMvc mvc;
    final OffsetDateTime dataCentral = OffsetDateTime.parse("2026-11-14T12:00:00Z");
    final UUID protocolo = UUID.randomUUID();

    @BeforeEach void preparar() {
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
    @Test void deveTratarConexaoRecusadaComoFalhaConhecida() {
        when(central.criarOperacao(any(), any())).thenThrow(new CentralIndisponivelException(
                new ResourceAccessException("conexão recusada", new java.net.ConnectException())));
        verificarFalha(StatusIntegracao.FALHA_ENVIO);
    }
    void verificarFalha(StatusIntegracao esperado) {
        UUID chave = UUID.randomUUID();
        assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), chave)).isInstanceOf(CentralIndisponivelException.class);
        assertThat(statusLocal(chave)).isEqualTo(esperado);
        assertThatThrownBy(() -> service.criarOperacao(request("BEN-1"), chave)).isInstanceOf(CentralIndisponivelException.class);
        assertThat(operacoes.count()).isEqualTo(1);
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
        verify(central, times(1)).criarOperacao(any(), eq(chave));
    }
    Object executar(CriarOperacaoRequestDTO request, UUID chave) {
        try { return service.criarOperacao(request, chave); }
        catch (RuntimeException excecao) { return excecao; }
    }
}
