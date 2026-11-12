package br.com.central.convenios.dto;

import br.com.central.convenios.entity.OperacaoPortabilidade;
import br.com.central.convenios.entity.StatusOperacao;
import java.util.UUID;

public record OperacaoRespostaDTO(UUID protocolo, StatusOperacao status) {
    public static OperacaoRespostaDTO de(OperacaoPortabilidade operacao) {
        return new OperacaoRespostaDTO(operacao.getProtocolo(), operacao.getStatus());
    }
}
