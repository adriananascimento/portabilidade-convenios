package br.com.central.convenios.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record OperacaoRequisicaoDTO(
        @NotBlank @Size(max = 100)
        String beneficiarioId,

        @NotEmpty
        List<@NotBlank @Size(max = 100) String> parcelasIds,

        @NotNull @Valid
        ContaDestinoDTO contaDestino
) {

    public record ContaDestinoDTO(
            @NotBlank @Size(max = 100)
            String banco,

            @NotBlank @Size(max = 100)
            String agencia,

            @NotBlank @Size(max = 100)
            String conta
    ) {}
}
