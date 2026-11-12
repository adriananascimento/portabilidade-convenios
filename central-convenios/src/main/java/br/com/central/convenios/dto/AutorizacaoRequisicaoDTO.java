package br.com.central.convenios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AutorizacaoRequisicaoDTO(
    @NotBlank(message = "O beneficiário é obrigatório")
    @Size(max = 100, message = "O beneficiário deve ter no máximo 100 caracteres")
    String beneficiarioId,

    @NotBlank(message = "O convênio é obrigatório")
    @Size(max = 100, message = "O convênio deve ter no máximo 100 caracteres")
    String convenioId
) {}
