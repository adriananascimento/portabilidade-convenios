package br.com.central.convenios.dto;

import br.com.central.convenios.entity.OperacaoPortabilidade;
import br.com.central.convenios.entity.StatusOperacao;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OperacaoConsultaRespostaDTO(UUID protocolo, StatusOperacao status,
        OffsetDateTime dataCriacao, OffsetDateTime dataProcessamento, String motivoRejeicao) {
    public static OperacaoConsultaRespostaDTO de(OperacaoPortabilidade operacao) {
        return new OperacaoConsultaRespostaDTO(operacao.getProtocolo(), operacao.getStatus(),
                operacao.getDataCriacao(), operacao.getDataProcessamento(), operacao.getMotivoRejeicao());
    }
}
