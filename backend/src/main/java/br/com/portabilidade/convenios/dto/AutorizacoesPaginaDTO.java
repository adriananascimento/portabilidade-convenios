package br.com.portabilidade.convenios.dto;

import java.util.List;

public record AutorizacoesPaginaDTO(List<AutorizacaoRespostaDTO> conteudo, int pagina, int tamanho,
                                    long totalElementos, int totalPaginas) {}
