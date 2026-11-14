package br.com.portabilidade.convenios.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

enum StatusOperacao {
    PENDENTE,
    CONFIRMADA,
    REJEITADA,
    FALHA_TECNICA
}

public record OperacaoRespostaDTO(
        UUID protocolo,
        StatusOperacao status,
        OffsetDateTime dataCriacao,
        String beneficiarioId
) {}
