package br.com.central.convenios.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "reservas_parcelas")
public class ReservaParcela {
    @Id
    @Column(name = "parcela_id", length = 100)
    private String parcelaId;
    @Column(name = "operacao_protocolo", nullable = false)
    private UUID operacaoProtocolo;

    protected ReservaParcela() {}

    public ReservaParcela(String parcelaId, UUID operacaoProtocolo) {
        this.parcelaId = parcelaId;
        this.operacaoProtocolo = operacaoProtocolo;
    }
}
