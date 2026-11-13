package br.com.portabilidade.convenios.exception;

public class CentralIndisponivelException extends RuntimeException {

    public CentralIndisponivelException(Throwable causa) {
        super("Central de Convênios indisponível", causa);
    }
}
