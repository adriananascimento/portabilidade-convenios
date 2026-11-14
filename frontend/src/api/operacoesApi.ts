import type {OperacoesPagina} from '../types/operacao'

export async function listarOperacoes(pagina: number, tamanho: number, signal: AbortSignal): Promise<OperacoesPagina> {
  const resposta = await fetch(`/api/operacoes?pagina=${pagina}&tamanho=${tamanho}`, {signal})
  if (!resposta.ok) throw new Error('Não foi possível listar as operações.')
  return resposta.json()
}
