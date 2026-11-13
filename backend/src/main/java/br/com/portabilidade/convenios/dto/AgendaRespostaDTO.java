package br.com.portabilidade.convenios.dto;

import java.util.List;

public record AgendaRespostaDTO(String beneficiarioId, List<ParcelaRespostaDTO> parcelas) {}
