package br.com.central.convenios.dto;

import br.com.central.convenios.entity.Parcela;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaRespostaDTO(
        String parcelaId,
        String convenioId,
        BigDecimal valor,
        LocalDate dataDesconto
) {
    public static ParcelaRespostaDTO de(Parcela parcela) {
        return new ParcelaRespostaDTO(
                parcela.getId(),
                parcela.getConvenioId(),
                parcela.getValor(),
                parcela.getDataDesconto());
    }
}
