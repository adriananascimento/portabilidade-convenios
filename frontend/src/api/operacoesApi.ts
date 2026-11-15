import {erroHttp} from './errosApi'
import type {AgendaResposta, CriarOperacaoRequest, OperacaoListagem, OperacaoResposta, OperacoesPagina, EventoOperacao} from '../types/operacao'

export async function sincronizarStatusOperacao(id: string, signal: AbortSignal): Promise<OperacaoListagem> {
  const resposta = await fetch(`/api/operacoes/${encodeURIComponent(id)}/sincronizar-status`, {method: 'POST', signal})
  if (!resposta.ok) throw await erroHttp(resposta, 'Não foi possível sincronizar o status da operação.')
  return resposta.json()
}

export async function listarOperacoes(pagina: number, tamanho: number, signal: AbortSignal): Promise<OperacoesPagina> {
  const resposta = await fetch(`/api/operacoes?pagina=${pagina}&tamanho=${tamanho}`, {signal})
  if (!resposta.ok) throw await erroHttp(resposta, 'Não foi possível listar as operações.')
  return resposta.json()
}

export async function consultarAgenda(beneficiarioId: string, signal: AbortSignal): Promise<AgendaResposta> {
  const resposta = await fetch(`/api/beneficiarios/${encodeURIComponent(beneficiarioId)}/agenda`, {signal})
  if (!resposta.ok) throw resposta.status === 404
    ? new Error('Beneficiário não encontrado.')
    : await erroHttp(resposta, 'Não foi possível consultar as parcelas.')
  return resposta.json()
}

export async function criarOperacao(dados: CriarOperacaoRequest, chave: string): Promise<OperacaoResposta> {
  const resposta = await fetch('/api/operacoes', {
    method: 'POST',
    headers: {'Content-Type': 'application/json', 'Idempotency-Key': chave},
    body: JSON.stringify(dados),
  })
  if (!resposta.ok) {
    const erro = await erroHttp(resposta, 'Não foi possível confirmar o recebimento.')
    if (resposta.status >= 500) erro.message += ' A operação pode ter sido criada. Consulte a listagem para acompanhar o resultado.'
    throw erro
  }
  return resposta.json()
}

export async function consultarEventos(id: string, signal: AbortSignal): Promise<EventoOperacao[]> {
  const resposta = await fetch(`/api/operacoes/${encodeURIComponent(id)}/eventos`, {signal})
  if (!resposta.ok) throw await erroHttp(resposta, 'Não foi possível consultar o histórico.')
  return resposta.json()
}
