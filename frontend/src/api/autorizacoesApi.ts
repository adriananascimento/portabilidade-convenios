import type {AutorizacaoResposta, CriarAutorizacaoRequest} from "../types/autorizacao.ts";

export async function criarAutorizacao(
  dados: CriarAutorizacaoRequest
): Promise<AutorizacaoResposta> {
  const resposta = await fetch('/api/autorizacoes', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(dados),
  })

  if (!resposta.ok) {
    throw new Error('Não foi possível criar a autorização.')
  }

  return resposta.json() as Promise<AutorizacaoResposta>
}

export async function listarAutorizacoes(pagina: number, tamanho: number, signal: AbortSignal): Promise<import('../types/autorizacao').AutorizacoesPagina> {
  const resposta = await fetch(`/api/autorizacoes?pagina=${pagina}&tamanho=${tamanho}`, {signal})
  if (!resposta.ok) throw new Error('Não foi possível listar as autorizações.')
  return resposta.json()
}
