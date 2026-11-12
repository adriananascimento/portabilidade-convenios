package br.com.central.convenios.dto;

import br.com.central.convenios.entity.Parcela;
import br.com.central.convenios.entity.StatusParcela;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaRespostaDTO(String parcelaId, String convenioId, BigDecimal valor,
                                 LocalDate dataDesconto, StatusParcela status) {
    public static ParcelaRespostaDTO de(Parcela parcela) {
        return new ParcelaRespostaDTO(parcela.getId(), parcela.getConvenioId(), parcela.getValor(),
                parcela.getDataDesconto(), parcela.getStatus());
    }
}
