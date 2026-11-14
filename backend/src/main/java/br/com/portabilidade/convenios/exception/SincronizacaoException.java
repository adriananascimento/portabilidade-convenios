package br.com.portabilidade.convenios.exception;

import org.springframework.http.HttpStatus;

public class SincronizacaoException extends RuntimeException {
    private final HttpStatus status;

    public SincronizacaoException(HttpStatus status, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
