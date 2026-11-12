package br.com.central.convenios;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CentralConveniosApplication {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

	public static void main(String[] args) {
		SpringApplication.run(CentralConveniosApplication.class, args);
	}

}
