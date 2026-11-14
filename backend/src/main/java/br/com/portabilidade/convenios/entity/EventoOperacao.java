package br.com.portabilidade.convenios.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "eventos_operacao")
public class EventoOperacao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operacao_id", nullable = false, updatable = false)
    private OperacaoPortabilidade operacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private TipoEventoOperacao tipo;

    @Column(nullable = false, updatable = false, length = 500)
    private String descricao;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_integracao_anterior", updatable = false, length = 30)
    private StatusIntegracao statusIntegracaoAnterior;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_integracao_novo", updatable = false, length = 30)
    private StatusIntegracao statusIntegracaoNovo;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_operacao_anterior", updatable = false, length = 20)
    private StatusOperacao statusOperacaoAnterior;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_operacao_novo", updatable = false, length = 20)
    private StatusOperacao statusOperacaoNovo;
    @Column(name = "protocolo_central", updatable = false)
    private UUID protocoloCentral;

    protected EventoOperacao() {}

    public EventoOperacao(OperacaoPortabilidade operacao, TipoEventoOperacao tipo, String descricao,
            StatusIntegracao anterior, StatusIntegracao novo, StatusOperacao operacaoAnterior,
            StatusOperacao operacaoNovo, UUID protocoloCentral) {
        this.operacao = operacao;
        this.tipo = tipo;
        this.descricao = descricao;
        this.statusIntegracaoAnterior = anterior;
        this.statusIntegracaoNovo = novo;
        this.statusOperacaoAnterior = operacaoAnterior;
        this.statusOperacaoNovo = operacaoNovo;
        this.protocoloCentral = protocoloCentral;
        this.dataCriacao = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public TipoEventoOperacao getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
    public StatusIntegracao getStatusIntegracaoAnterior() { return statusIntegracaoAnterior; }
    public StatusIntegracao getStatusIntegracaoNovo() { return statusIntegracaoNovo; }
    public StatusOperacao getStatusOperacaoAnterior() { return statusOperacaoAnterior; }
    public StatusOperacao getStatusOperacaoNovo() { return statusOperacaoNovo; }
    public UUID getProtocoloCentral() { return protocoloCentral; }
}
