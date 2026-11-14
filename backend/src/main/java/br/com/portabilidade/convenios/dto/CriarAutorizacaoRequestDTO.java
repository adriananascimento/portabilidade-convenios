package br.com.portabilidade.convenios.dto;

public record CriarAutorizacaoRequestDTO(
        String beneficiarioId,
        String convenioId
) {}
