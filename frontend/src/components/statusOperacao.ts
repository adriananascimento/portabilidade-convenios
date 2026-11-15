import type {StatusIntegracao, StatusOperacao} from '../types/operacao'

export const coresStatus: Record<StatusOperacao, 'warning' | 'success' | 'error'> = {
  PENDENTE: 'warning', CONFIRMADA: 'success', REJEITADA: 'error', FALHA_TECNICA: 'error',
}
export const rotulosStatus: Record<StatusOperacao, string> = {
  PENDENTE: 'Pendente', CONFIRMADA: 'Confirmada', REJEITADA: 'Rejeitada', FALHA_TECNICA: 'Falha técnica',
}
export const integracoes: Record<StatusIntegracao, {rotulo: string; cor: 'warning' | 'success' | 'error'}> = {
  PENDENTE_ENVIO: {rotulo: 'Pendente de envio', cor: 'warning'},
  ENVIADA: {rotulo: 'Enviada', cor: 'success'},
  FALHA_ENVIO: {rotulo: 'Falha no envio', cor: 'error'},
  ENVIO_INDETERMINADO: {rotulo: 'Envio indeterminado', cor: 'warning'},
}
