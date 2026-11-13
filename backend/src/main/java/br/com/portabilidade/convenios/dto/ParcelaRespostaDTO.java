package br.com.portabilidade.convenios.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaRespostaDTO(
        String parcelaId,
        String convenioId,
        BigDecimal valor,
        LocalDate dataDesconto
) {}
