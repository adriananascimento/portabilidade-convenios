import type {OperacaoListagem, OperacoesPagina} from '../types/operacao'

export function operacaoElegivel(operacao: OperacaoListagem): boolean {
  return Boolean(operacao.protocoloCentral)
    && (operacao.statusOperacao === null || operacao.statusOperacao === 'PENDENTE')
}

export function atualizarOperacao(pagina: OperacoesPagina, operacao: OperacaoListagem): OperacoesPagina {
  return {...pagina, conteudo: pagina.conteudo.map(atual => atual.id === operacao.id ? operacao : atual)}
}

interface OpcoesPolling {
  operacoes: OperacaoListagem[]
  signal: AbortSignal
  sincronizar: (id: string, signal: AbortSignal) => Promise<OperacaoListagem>
  aoAtualizar: (operacao: OperacaoListagem) => void
  aoConcluir: (houveFalha: boolean) => void
  intervalo?: number
}

// Uma instância pertence a uma única carga da página. O abort encerra toda essa instância.
export function iniciarPollingOperacoes({operacoes, signal, sincronizar, aoAtualizar, aoConcluir,
  intervalo = 10_000}: OpcoesPolling): void {
  let atuais = [...new Map(operacoes.map(operacao => [operacao.id, operacao])).values()]
  let temporizador: ReturnType<typeof setTimeout> | undefined

  function cancelar() {
    clearTimeout(temporizador)
    signal.removeEventListener('abort', cancelar)
  }

  function agendar() {
    if (signal.aborted || !atuais.some(operacaoElegivel)) {
      cancelar()
      return
    }
    temporizador = setTimeout(() => {void executar()}, intervalo)
  }

  async function executar() {
    let houveFalha = false
    for (const operacao of atuais.filter(operacaoElegivel)) {
      if (signal.aborted) return
      try {
        const resposta = await sincronizar(operacao.id, signal)
        if (signal.aborted) return
        if (resposta.id !== operacao.id) throw new Error('Resposta de outra operação.')
        atuais = atuais.map(atual => atual.id === resposta.id ? resposta : atual)
        aoAtualizar(resposta)
      } catch (erro) {
        if (signal.aborted) return
        if (erro instanceof Error && erro.name === 'AbortError') continue
        houveFalha = true
      }
    }
    if (signal.aborted) return
    aoConcluir(houveFalha)
    agendar()
  }

  if (signal.aborted) return
  signal.addEventListener('abort', cancelar, {once: true})
  agendar()
}
