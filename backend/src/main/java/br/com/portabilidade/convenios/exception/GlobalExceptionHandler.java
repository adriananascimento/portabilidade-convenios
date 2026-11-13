package br.com.portabilidade.convenios.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CentralIndisponivelException.class)
    public ResponseEntity<ProblemDetail> tratarCentralIndisponivel(CentralIndisponivelException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);

        problema.setTitle("Serviço indisponível");
        problema.setDetail("Não foi possível comunicar com a Central de Convênios.");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problema);
    }
}
