package br.com.central.convenios.operacao;

import br.com.central.convenios.BaseCentralConveniosTest;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigracaoReservasIntegracaoTest extends BaseCentralConveniosTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void deveMigrarVinculosExistentesPreservandoHistoricoEProtegendoReservas() {
        String esquema = "migracao_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(jdbc.getDataSource()).schemas(esquema).defaultSchema(esquema)
                    .target("6").load().migrate();
            var pendente = inserir(esquema, "PENDENTE", "P1");
            var confirmada = inserir(esquema, "CONFIRMADA", "P2");
            var rejeitada = inserir(esquema, "REJEITADA", "P3");
            Flyway.configure().dataSource(jdbc.getDataSource()).schemas(esquema).defaultSchema(esquema)
                    .load().migrate();
            assertThat(jdbc.queryForList("SELECT operacao_protocolo FROM " + esquema + ".reservas_parcelas", UUID.class))
                    .containsExactlyInAnyOrder(pendente, confirmada);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + esquema + ".operacoes_parcelas",
                    Integer.class)).isEqualTo(3);
            // Um novo vínculo pode repetir a parcela rejeitada, sem destruir o anterior.
            jdbc.update("INSERT INTO " + esquema + ".operacoes_parcelas VALUES (?, 'P3')", pendente);
            jdbc.update("INSERT INTO " + esquema + ".reservas_parcelas VALUES ('P3', ?)", pendente);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + esquema + ".operacoes_parcelas WHERE parcela_id = 'P3'",
                    Integer.class)).isEqualTo(2);
            // A unicidade ativa e a referência ao vínculo histórico são restrições do banco.
            assertThatThrownBy(() -> jdbc.update("INSERT INTO " + esquema + ".reservas_parcelas VALUES ('P3', ?)", rejeitada))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("DELETE FROM " + esquema + ".reservas_parcelas WHERE parcela_id = 'P3'");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO " + esquema + ".reservas_parcelas VALUES ('P3', ?)", confirmada))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + esquema + " CASCADE");
        }
    }

    private UUID inserir(String esquema, String status, String parcela) {
        var protocolo = UUID.randomUUID();
        jdbc.update("INSERT INTO " + esquema + ".parcelas VALUES (?, 'BEN', 'CONV', 100, DATE '2099-01-01', 'DISPONIVEL')", parcela);
        jdbc.update("INSERT INTO " + esquema + ".operacoes_portabilidade "
                + "(protocolo, chave_idempotencia, beneficiario_id, banco, agencia, conta, status, data_criacao) "
                + "VALUES (?, ?, 'BEN', '001', '1', '1', ?, CURRENT_TIMESTAMP)", protocolo, UUID.randomUUID(), status);
        jdbc.update("INSERT INTO " + esquema + ".operacoes_parcelas VALUES (?, ?)", protocolo, parcela);
        return protocolo;
    }
}
