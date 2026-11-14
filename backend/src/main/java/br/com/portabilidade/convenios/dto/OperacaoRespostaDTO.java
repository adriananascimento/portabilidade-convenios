package br.com.portabilidade.convenios.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import br.com.portabilidade.convenios.entity.StatusOperacao;

public record OperacaoRespostaDTO(
        UUID protocolo,
        StatusOperacao status,
        OffsetDateTime dataCriacao,
        String beneficiarioId
) {}
