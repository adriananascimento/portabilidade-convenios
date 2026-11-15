// Somente mensagens públicas conhecidas do contrato; corpos arbitrários nunca vão à tela.
const detalhesPublicos = new Set([
  'Falha de autenticação na comunicação com a Central de Convênios.',
  'Não foi possível comunicar com a Central de Convênios.',
  'Operação não encontrada',
  'A lista contém parcelas repetidas',
  'Página deve ser não negativa e tamanho deve estar entre 1 e 100.',
  'Chave de idempotência reutilizada com conteúdo diferente',
  'Operação alterada durante a consulta; resposta antiga não aplicada',
  'Operação sem protocolo da Central; sincronização indisponível',
])

export async function erroHttp(resposta: Response, contexto: string): Promise<Error> {
  let detalhe: unknown
  try { detalhe = (await resposta.json()).detail } catch { /* Respostas sem JSON usam mensagem local. */ }
  if (typeof detalhe === 'string' && detalhesPublicos.has(detalhe)) return new Error(detalhe)
  const mensagens: Record<number, string> = {
    400: 'Solicitação inválida. Confira os dados informados.',
    401: 'Sua autenticação é necessária. Entre novamente para continuar.',
    403: 'Você não tem permissão para realizar esta ação.',
    404: 'Registro não encontrado.',
    409: 'Conflito na solicitação. A operação pode já existir; confira os dados e a listagem.',
    502: 'Falha na comunicação ou autenticação entre o Portal e a Central. Tente novamente mais tarde.',
    503: 'Serviço temporariamente indisponível. Tente novamente mais tarde.',
    504: 'O serviço demorou para responder. Tente novamente mais tarde.',
  }
  return new Error(`${contexto} ${mensagens[resposta.status] ?? 'Tente novamente mais tarde.'}`)
}

export function mensagemErro(falha: unknown, padrao: string): string {
  if (falha instanceof SyntaxError) return padrao
  if (falha instanceof TypeError) return 'Falha de rede. Verifique sua conexão e tente novamente.'
  return falha instanceof Error ? falha.message : padrao
}
