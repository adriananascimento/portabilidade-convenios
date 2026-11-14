package br.com.central.convenios.dto;

import java.util.List;

public record AutorizacaoPaginaDTO(List<AutorizacaoRespostaDTO> conteudo, int pagina, int tamanho,
                                   long totalElementos, int totalPaginas) {}
