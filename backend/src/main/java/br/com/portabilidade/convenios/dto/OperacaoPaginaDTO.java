package br.com.portabilidade.convenios.dto;

import java.util.List;

public record OperacaoPaginaDTO(List<OperacaoRespostaDTO> conteudo, int pagina, int tamanho,
                                long totalElementos, int totalPaginas) {}
