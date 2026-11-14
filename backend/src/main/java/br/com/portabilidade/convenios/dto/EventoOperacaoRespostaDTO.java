package br.com.portabilidade.convenios.dto;

import br.com.portabilidade.convenios.entity.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EventoOperacaoRespostaDTO(UUID id, TipoEventoOperacao tipo, String descricao,
        OffsetDateTime dataCriacao, StatusIntegracao statusIntegracaoAnterior,
        StatusIntegracao statusIntegracaoNovo, StatusOperacao statusOperacaoAnterior,
        StatusOperacao statusOperacaoNovo, UUID protocoloCentral) {
    public static EventoOperacaoRespostaDTO de(EventoOperacao evento) {
        return new EventoOperacaoRespostaDTO(evento.getId(), evento.getTipo(), evento.getDescricao(),
                evento.getDataCriacao(), evento.getStatusIntegracaoAnterior(), evento.getStatusIntegracaoNovo(),
                evento.getStatusOperacaoAnterior(), evento.getStatusOperacaoNovo(), evento.getProtocoloCentral());
    }
}
