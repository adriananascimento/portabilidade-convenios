package br.com.portabilidade.convenios.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient centralRestClient(RestClient.Builder builder, @Value("${central.convenios.url}") String url) {

        var requestFactory = new SimpleClientHttpRequestFactory();

        // Tempo máximo para estabelecer a conexão com o servidor remoto (em segundos)
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));

        // Tempo máximo para aguardar a resposta do servidor remoto (em segundos)
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return builder.baseUrl(url).build();
    }
}
