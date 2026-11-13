package br.com.central.convenios.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "parcelas")
public class Parcela {
    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "beneficiario_id", nullable = false, length = 100)
    private String beneficiarioId;

    @Column(name = "convenio_id", nullable = false, length = 100)
    private String convenioId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_desconto", nullable = false)
    private LocalDate dataDesconto;

    protected Parcela() {}

    public String getBeneficiarioId() { return beneficiarioId; }
    public String getId() { return id; }
    public String getConvenioId() { return convenioId; }
    public BigDecimal getValor() { return valor; }
    public LocalDate getDataDesconto() { return dataDesconto; }
}
