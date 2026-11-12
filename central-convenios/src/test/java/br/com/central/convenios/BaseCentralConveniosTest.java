package br.com.central.convenios;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "central.processamento.habilitado=false"
)
@Import(BaseCentralConveniosTest.ConfiguracaoContainers.class)
public abstract class BaseCentralConveniosTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracaoContainers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("central_convenios_testes")
                    .withUsername("postgres")
                    .withPassword("postgres");
        }
    }
}
