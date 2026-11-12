package br.com.central.convenios.dto;

import br.com.central.convenios.entity.Autorizacao;
import br.com.central.convenios.entity.StatusAutorizacao;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AutorizacaoRespostaDTO(UUID autorizacaoId, String beneficiarioId, String convenioId,
                                     OffsetDateTime dataAutorizacao, StatusAutorizacao status) {

    public static AutorizacaoRespostaDTO de(Autorizacao autorizacao) {
        return new AutorizacaoRespostaDTO(
                autorizacao.getId(),
                autorizacao.getBeneficiarioId(),
                autorizacao.getConvenioId(),
                autorizacao.getDataAutorizacao(),
                autorizacao.getStatus()
        );
    }
}
