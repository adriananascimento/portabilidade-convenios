package br.com.portabilidade.convenios.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SincronizacaoException.class)
    public ProblemDetail tratarSincronizacao(SincronizacaoException excecao) {
        return ProblemDetail.forStatusAndDetail(excecao.getStatus(), excecao.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail tratarNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, excecao.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail tratarConflito(ConflitoException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, excecao.getMessage());
    }

    @ExceptionHandler(CentralIndisponivelException.class)
    public ResponseEntity<ProblemDetail> tratarCentralIndisponivel(CentralIndisponivelException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);

        problema.setTitle("Serviço indisponível");
        problema.setDetail(excecao.getCause() == null ? excecao.getMessage()
                : "Não foi possível comunicar com a Central de Convênios.");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problema);
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ProblemDetail handleRequisicaoInvalida(RequisicaoInvalidaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problema.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        return problema;
    }
}
