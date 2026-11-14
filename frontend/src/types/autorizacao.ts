export interface CriarAutorizacaoRequest {
  beneficiarioId: string
  convenioId: string
}

export interface AutorizacaoResposta {
  autorizacaoId: string
  beneficiarioId: string
  convenioId: string
  dataAutorizacao: string
  status: string
}

export interface AutorizacoesPagina {
  conteudo: AutorizacaoResposta[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}
