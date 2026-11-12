package br.com.central.convenios.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "autorizacoes")
public class Autorizacao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "beneficiario_id", nullable = false, length = 100)
    private String beneficiarioId;

    @Column(name = "convenio_id", nullable = false, length = 100)
    private String convenioId;

    @Column(name = "data_autorizacao", nullable = false)
    private OffsetDateTime dataAutorizacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAutorizacao status;

    protected Autorizacao() {}

    public Autorizacao(String beneficiarioId, String convenioId) {
        this.beneficiarioId = beneficiarioId;
        this.convenioId = convenioId;
        this.dataAutorizacao = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        this.status = StatusAutorizacao.ATIVA;
    }

    public UUID getId() { return id; }
    public String getBeneficiarioId() { return beneficiarioId; }
    public String getConvenioId() { return convenioId; }
    public OffsetDateTime getDataAutorizacao() { return dataAutorizacao; }
    public StatusAutorizacao getStatus() { return status; }
}
