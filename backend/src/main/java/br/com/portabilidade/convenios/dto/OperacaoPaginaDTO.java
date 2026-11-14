package br.com.portabilidade.convenios.dto;

import java.util.List;

public record OperacaoPaginaDTO(List<OperacaoListagemDTO> conteudo, int pagina, int tamanho,
                                long totalElementos, int totalPaginas) {}
