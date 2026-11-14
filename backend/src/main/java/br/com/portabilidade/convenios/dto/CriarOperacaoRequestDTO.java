package br.com.portabilidade.convenios.dto;

import java.util.List;

record ContaDestinoDTO(
        String banco,
        String agencia,
        String conta
) {}

public record CriarOperacaoRequestDTO(
        String beneficiarioId,
        List<String> parcelasIds,
        ContaDestinoDTO contaDestino
) {}
