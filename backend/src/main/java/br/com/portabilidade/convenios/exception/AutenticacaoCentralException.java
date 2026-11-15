package br.com.portabilidade.convenios.exception;

public class AutenticacaoCentralException extends RuntimeException {
    public AutenticacaoCentralException(Throwable causa) {
        super("Falha de autenticação na comunicação com a Central de Convênios.", causa);
    }
}
