export type StatusOperacao = 'PENDENTE' | 'CONFIRMADA' | 'REJEITADA' | 'FALHA_TECNICA'

export interface OperacaoResposta {
  protocolo: string
  status: StatusOperacao
  dataCriacao: string
  beneficiarioId: string
}

export interface OperacoesPagina {
  conteudo: OperacaoResposta[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}
