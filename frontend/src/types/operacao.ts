export type StatusOperacao = 'PENDENTE' | 'CONFIRMADA' | 'REJEITADA' | 'FALHA_TECNICA'

export interface OperacaoResposta {
  protocolo: string
  status: StatusOperacao
  dataCriacao: string
  beneficiarioId: string
}

export type StatusIntegracao = 'PENDENTE_ENVIO' | 'ENVIADA' | 'FALHA_ENVIO' | 'ENVIO_INDETERMINADO'

export interface OperacaoListagem {
  id: string
  beneficiarioId: string
  dataCriacao: string
  statusIntegracao: StatusIntegracao
  statusOperacao: StatusOperacao | null
  protocoloCentral: string | null
}

export interface OperacoesPagina {
  conteudo: OperacaoListagem[]
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

export type TipoEventoOperacao = 'OPERACAO_CRIADA_LOCALMENTE' | 'TENTATIVA_ENVIO_CENTRAL'
  | 'PROTOCOLO_RECEBIDO' | 'FALHA_INTEGRACAO' | 'STATUS_OPERACAO_ATUALIZADO' | 'RESULTADO_INDETERMINADO'

export interface EventoOperacao {
  id: string
  tipo: TipoEventoOperacao
  descricao: string | null
  dataCriacao: string
  statusIntegracaoAnterior: StatusIntegracao | null
  statusIntegracaoNovo: StatusIntegracao | null
  statusOperacaoAnterior: StatusOperacao | null
  statusOperacaoNovo: StatusOperacao | null
  protocoloCentral: string | null
}
