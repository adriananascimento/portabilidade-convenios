package br.com.portabilidade.convenios.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AutorizacaoRespostaDTO (
        UUID autorizacaoId,
        String beneficiarioId,
        String convenioId,
        OffsetDateTime dataAutorizacao,
        String status
) {}
