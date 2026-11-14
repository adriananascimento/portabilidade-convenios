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

export interface ParcelaResposta {
  parcelaId: string
  convenioId: string
  valor: number
  dataDesconto: string
}

export interface AgendaResposta {
  beneficiarioId: string
  parcelas: ParcelaResposta[]
}

export interface CriarOperacaoRequest {
  beneficiarioId: string
  parcelasIds: string[]
  contaDestino: {banco: string; agencia: string; conta: string}
}
