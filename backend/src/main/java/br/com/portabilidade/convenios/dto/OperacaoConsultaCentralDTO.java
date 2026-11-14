package br.com.portabilidade.convenios.dto;

import br.com.portabilidade.convenios.entity.StatusOperacao;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OperacaoConsultaCentralDTO(UUID protocolo, StatusOperacao status,
        OffsetDateTime dataCriacao, OffsetDateTime dataProcessamento, String motivoRejeicao) {}
