package br.com.portabilidade.convenios.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "operacoes_portabilidade")
public class OperacaoPortabilidade {

    @Version
    @Column(nullable = false)
    private long versao;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chave_idempotencia", nullable = false, unique = true, updatable = false)
    private UUID chaveIdempotencia;

    @Column(name = "protocolo_central")
    private UUID protocoloCentral;

    @Column(name = "data_criacao_central")
    private OffsetDateTime dataCriacaoCentral;

    @Column(name = "beneficiario_id", nullable = false, length = 100)
    private String beneficiarioId;

    @ElementCollection
    @CollectionTable(name = "operacoes_portabilidade_parcelas",
            joinColumns = @JoinColumn(name = "operacao_id"))
    @Column(name = "parcela_id", nullable = false, length = 100)
    private Set<String> parcelasIds = new HashSet<>();

    @Embedded
    private ContaDestino contaDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_integracao", nullable = false, length = 30)
    private StatusIntegracao statusIntegracao = StatusIntegracao.PENDENTE_ENVIO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_operacao", length = 20)
    private StatusOperacao statusOperacao;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private OffsetDateTime dataAtualizacao;

    protected OperacaoPortabilidade() {}

    public OperacaoPortabilidade(UUID chaveIdempotencia, String beneficiarioId,
            Set<String> parcelasIds, ContaDestino contaDestino) {
        this.chaveIdempotencia = chaveIdempotencia;
        this.beneficiarioId = beneficiarioId;
        this.parcelasIds = new HashSet<>(parcelasIds);
        this.contaDestino = contaDestino;
    }

    @PrePersist
    private void inicializarDatas() {
        dataCriacao = OffsetDateTime.now(ZoneOffset.UTC);
        dataAtualizacao = dataCriacao;
    }

    @PreUpdate
    private void atualizarData() {
        dataAtualizacao = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public OffsetDateTime getDataCriacaoCentral() { return dataCriacaoCentral; }
    public void setDataCriacaoCentral(OffsetDateTime data) { dataCriacaoCentral = data; }

    public long getVersao() { return versao; }

    public UUID getId() { return id; }
    public UUID getChaveIdempotencia() { return chaveIdempotencia; }
    public UUID getProtocoloCentral() { return protocoloCentral; }
    public String getBeneficiarioId() { return beneficiarioId; }
    public Set<String> getParcelasIds() { return Set.copyOf(parcelasIds); }
    public ContaDestino getContaDestino() { return contaDestino; }
    public StatusIntegracao getStatusIntegracao() { return statusIntegracao; }
    public StatusOperacao getStatusOperacao() { return statusOperacao; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
    public OffsetDateTime getDataAtualizacao() { return dataAtualizacao; }

    public void setProtocoloCentral(UUID protocoloCentral) { this.protocoloCentral = protocoloCentral; }
    public void setStatusIntegracao(StatusIntegracao statusIntegracao) { this.statusIntegracao = statusIntegracao; }
    public void setStatusOperacao(StatusOperacao statusOperacao) { this.statusOperacao = statusOperacao; }
}
