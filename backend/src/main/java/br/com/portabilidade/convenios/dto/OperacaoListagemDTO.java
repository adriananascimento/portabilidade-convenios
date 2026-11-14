package br.com.portabilidade.convenios.dto;

import br.com.portabilidade.convenios.entity.StatusIntegracao;
import br.com.portabilidade.convenios.entity.StatusOperacao;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OperacaoListagemDTO(
        UUID id,
        String beneficiarioId,
        OffsetDateTime dataCriacao,
        StatusIntegracao statusIntegracao,
        StatusOperacao statusOperacao,
        UUID protocoloCentral
) {}
