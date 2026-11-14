import type {AgendaResposta, CriarOperacaoRequest, OperacaoListagem, OperacaoResposta, OperacoesPagina} from '../types/operacao'

export async function sincronizarStatusOperacao(id: string, signal: AbortSignal): Promise<OperacaoListagem> {
  const resposta = await fetch(`/api/operacoes/${encodeURIComponent(id)}/sincronizar-status`, {method: 'POST', signal})
  if (!resposta.ok) throw new Error('Não foi possível sincronizar o status da operação.')
  return resposta.json()
}

export async function listarOperacoes(pagina: number, tamanho: number, signal: AbortSignal): Promise<OperacoesPagina> {
  const resposta = await fetch(`/api/operacoes?pagina=${pagina}&tamanho=${tamanho}`, {signal})
  if (!resposta.ok) throw new Error('Não foi possível listar as operações.')
  return resposta.json()
}

export async function consultarAgenda(beneficiarioId: string, signal: AbortSignal): Promise<AgendaResposta> {
  const resposta = await fetch(`/api/beneficiarios/${encodeURIComponent(beneficiarioId)}/agenda`, {signal})
  if (!resposta.ok) throw new Error(resposta.status === 404
    ? 'Beneficiário não encontrado.'
    : 'Não foi possível consultar as parcelas. Tente novamente.')
  return resposta.json()
}

export async function criarOperacao(dados: CriarOperacaoRequest, chave: string): Promise<OperacaoResposta> {
  const resposta = await fetch('/api/operacoes', {
    method: 'POST',
    headers: {'Content-Type': 'application/json', 'Idempotency-Key': chave},
    body: JSON.stringify(dados),
  })
  if (!resposta.ok) {
    if (resposta.status === 409) throw new Error('Conflito na solicitação. Confira as parcelas e os dados informados.')
    if (resposta.status === 400) throw new Error('Solicitação inválida. Confira os dados informados.')
    throw new Error('Não foi possível confirmar o recebimento. A operação pode ter sido criada. Reenvie sem alterar os dados para reutilizar a mesma chave.')
  }
  return resposta.json()
}
