package br.com.portabilidade.convenios.operacao;

import br.com.portabilidade.convenios.entity.*;
import br.com.portabilidade.convenios.repository.OperacaoPortabilidadeRepository;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperacaoPersistenciaTest {
    private static final String SCHEMA = "teste_operacoes_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void configurarSchema(DynamicPropertyRegistry registro) {
        registro.add("spring.flyway.schemas", () -> SCHEMA);
        registro.add("spring.flyway.default-schema", () -> SCHEMA);
        registro.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @Autowired private OperacaoPortabilidadeRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    @AfterAll
    void removerSchemaTemporario() {
        new JdbcTemplate(dataSource).execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    private OperacaoPortabilidade novaOperacao(UUID chave) {
        return new OperacaoPortabilidade(chave, "BEN-001", Set.of("PAR-001", "PAR-002"),
                new ContaDestino("001", "0001", "001234-5"));
    }

    @Test
    void devePersistirDadosSemProtocoloNemStatusOficial() {
        UUID chave = UUID.randomUUID();
        var operacao = repository.saveAndFlush(novaOperacao(chave));
        entityManager.clear();
        var salva = repository.findByChaveIdempotencia(chave).orElseThrow();
        assertThat(salva.getId()).isNotNull().isEqualTo(operacao.getId());
        assertThat(salva.getProtocoloCentral()).isNull();
        assertThat(salva.getStatusOperacao()).isNull();
        assertThat(salva.getStatusIntegracao()).isEqualTo(StatusIntegracao.PENDENTE_ENVIO);
        assertThat(salva.getBeneficiarioId()).isEqualTo("BEN-001");
        assertThat(salva.getParcelasIds()).containsExactlyInAnyOrder("PAR-001", "PAR-002");
        assertThat(salva.getContaDestino().getBanco()).isEqualTo("001");
        assertThat(salva.getContaDestino().getAgencia()).isEqualTo("0001");
        assertThat(salva.getContaDestino().getConta()).isEqualTo("001234-5");
        assertThat(salva.getDataCriacao()).isNotNull();
        assertThat(salva.getDataAtualizacao()).isEqualTo(salva.getDataCriacao());
    }

    @Test
    void deveAtualizarStatusProtocoloEDataPreservandoCriacao() {
        var operacao = repository.saveAndFlush(novaOperacao(UUID.randomUUID()));
        // Data anterior determinística, sem espera pela resolução do relógio.
        entityManager.createNativeQuery("UPDATE " + SCHEMA
                + ".operacoes_portabilidade SET data_atualizacao = data_criacao - interval '1 day' WHERE id = :id")
                .setParameter("id", operacao.getId()).executeUpdate();
        entityManager.clear();
        operacao = repository.findById(operacao.getId()).orElseThrow();
        var criacao = operacao.getDataCriacao();
        var anterior = operacao.getDataAtualizacao();
        UUID protocolo = UUID.randomUUID();
        operacao.setProtocoloCentral(protocolo);
        operacao.setStatusIntegracao(StatusIntegracao.ENVIADA);
        operacao.setStatusOperacao(StatusOperacao.PENDENTE);
        repository.flush();
        entityManager.clear();
        var salva = repository.findById(operacao.getId()).orElseThrow();
        assertThat(salva.getProtocoloCentral()).isEqualTo(protocolo);
        assertThat(salva.getStatusIntegracao()).isEqualTo(StatusIntegracao.ENVIADA);
        assertThat(salva.getStatusOperacao()).isEqualTo(StatusOperacao.PENDENTE);
        assertThat(salva.getDataCriacao()).isEqualTo(criacao);
        assertThat(salva.getDataAtualizacao()).isAfter(anterior);
    }

    @Test
    void deveRejeitarChaveIdempotenciaDuplicada() {
        UUID chave = UUID.randomUUID();
        repository.saveAndFlush(novaOperacao(chave));
        assertThatThrownBy(() -> repository.saveAndFlush(novaOperacao(chave)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void devePermitirMesmaParcelaEmOperacoesDistintas() {
        repository.saveAndFlush(novaOperacao(UUID.randomUUID()));
        repository.saveAndFlush(novaOperacao(UUID.randomUUID()));
        assertThat(repository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"chave_idempotencia", "beneficiario_id", "banco", "agencia", "conta",
            "status_integracao", "data_criacao", "data_atualizacao"})
    void deveRejeitarCamposObrigatoriosNulos(String coluna) {
        var operacao = repository.saveAndFlush(novaOperacao(UUID.randomUUID()));
        assertThatThrownBy(() -> entityManager.createNativeQuery("UPDATE " + SCHEMA
                + ".operacoes_portabilidade SET " + coluna + " = NULL WHERE id = :id")
                .setParameter("id", operacao.getId()).executeUpdate())
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status_integracao", "status_operacao"})
    void deveRejeitarStatusForaDoDominio(String coluna) {
        var operacao = repository.saveAndFlush(novaOperacao(UUID.randomUUID()));
        assertThatThrownBy(() -> entityManager.createNativeQuery("UPDATE " + SCHEMA
                + ".operacoes_portabilidade SET " + coluna + " = 'INVALIDO' WHERE id = :id")
                .setParameter("id", operacao.getId()).executeUpdate())
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void deveRejeitarParcelaDuplicadaNaMesmaOperacao() {
        var operacao = repository.saveAndFlush(novaOperacao(UUID.randomUUID()));
        assertThatThrownBy(() -> inserirParcela(operacao.getId(), "PAR-001"))
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void deveRejeitarParcelaSemOperacaoLocal() {
        assertThatThrownBy(() -> inserirParcela(UUID.randomUUID(), "PAR-001"))
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void deveRejeitarIdentificadorDeParcelaNulo() {
        var operacao = repository.saveAndFlush(novaOperacao(UUID.randomUUID()));
        assertThatThrownBy(() -> inserirParcela(operacao.getId(), null))
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    private void inserirParcela(UUID operacaoId, String parcelaId) {
        entityManager.createNativeQuery("INSERT INTO " + SCHEMA
                + ".operacoes_portabilidade_parcelas (operacao_id, parcela_id) VALUES (:operacao, :parcela)")
                .setParameter("operacao", operacaoId).setParameter("parcela", parcelaId).executeUpdate();
    }
}
