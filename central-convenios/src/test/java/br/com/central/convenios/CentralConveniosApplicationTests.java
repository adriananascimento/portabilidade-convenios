package br.com.central.convenios;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "central.processamento.habilitado=false")
class CentralConveniosApplicationTests {

	@Test
	void contextLoads() {
	}

}
