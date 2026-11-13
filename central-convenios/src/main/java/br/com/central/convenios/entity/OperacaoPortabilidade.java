package br.com.central.convenios.entity;

import br.com.central.convenios.dto.OperacaoRequisicaoDTO;
import br.com.central.convenios.exception.RegraNegocioException;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "operacoes_portabilidade")
public class OperacaoPortabilidade {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID protocolo;

    @Column(name = "chave_idempotencia", nullable = false, unique = true)
    private UUID chaveIdempotencia;

    @Column(name = "beneficiario_id", nullable = false, length = 100)
    private String beneficiarioId;

    @Column(nullable = false, length = 100)
    private String banco;

    @Column(nullable = false, length = 100)
    private String agencia;

    @Column(nullable = false, length = 100)
    private String conta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOperacao status;

    @Column(name = "data_criacao", nullable = false)
    private OffsetDateTime dataCriacao;

    @Column(name = "data_processamento")
    private OffsetDateTime dataProcessamento;

    @Column(name = "motivo_rejeicao")
    private String motivoRejeicao;

    @ElementCollection
    @CollectionTable(name = "operacoes_parcelas", joinColumns = @JoinColumn(name = "operacao_protocolo"))
    @Column(name = "parcela_id", nullable = false, length = 100)
    private Set<String> parcelasIds = new HashSet<>();

    protected OperacaoPortabilidade() {}

    public OperacaoPortabilidade(
            UUID chave,
            String beneficiarioId,
            String banco,
            String agencia,
            String conta,
            OffsetDateTime data
    ) {
        this.chaveIdempotencia = chave;
        this.beneficiarioId = beneficiarioId;
        this.banco = banco;
        this.agencia = agencia;
        this.conta = conta;
        status = StatusOperacao.PENDENTE;
        this.dataCriacao = data;
    }

    public boolean mesmoConteudo(
            String beneficiarioId,
            String banco,
            String agencia,
            String conta,
            List<String> parcelasIds
    ) {
        return this.beneficiarioId.equals(beneficiarioId)
                && this.banco.equals(banco)
                && this.agencia.equals(agencia)
                && this.conta.equals(conta)
                && this.parcelasIds.equals(new HashSet<>(parcelasIds));
    }

    public void finalizar(OffsetDateTime data, String motivo) {
        if (status != StatusOperacao.PENDENTE) {
            throw new RegraNegocioException("Operação já finalizada");
        }
        status = motivo == null ? StatusOperacao.CONFIRMADA : StatusOperacao.REJEITADA;
        dataProcessamento = data;
        motivoRejeicao = motivo;
    }

    public String getBeneficiarioId() {
        return beneficiarioId;
    }

    public Set<String> getParcelasIds() {
        return Set.copyOf(parcelasIds);
    }

    public void vincularParcelas(Set<String> ids) {
        parcelasIds.addAll(ids);
    }

    public OffsetDateTime getDataCriacao() {
        return dataCriacao;
    }

    public OffsetDateTime getDataProcessamento() {
        return dataProcessamento;
    }

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    public UUID getProtocolo() {
        return protocolo;
    }

    public StatusOperacao getStatus() {
        return status;
    }
}
