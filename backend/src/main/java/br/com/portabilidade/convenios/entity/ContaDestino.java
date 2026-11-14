package br.com.portabilidade.convenios.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ContaDestino {

    @Column(nullable = false, length = 100)
    private String banco;

    @Column(nullable = false, length = 100)
    private String agencia;

    @Column(nullable = false, length = 100)
    private String conta;

    protected ContaDestino() {}

    public ContaDestino(String banco, String agencia, String conta) {
        this.banco = banco;
        this.agencia = agencia;
        this.conta = conta;
    }

    public String getBanco() { return banco; }
    public String getAgencia() { return agencia; }
    public String getConta() { return conta; }
}
