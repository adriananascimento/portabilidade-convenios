import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import ts from 'typescript'

const fonte = await readFile(new URL('../src/polling/pollingOperacoes.ts', import.meta.url), 'utf8')
const codigo = ts.transpileModule(fonte, {compilerOptions: {module: ts.ModuleKind.ESNext}}).outputText
const {iniciarPollingOperacoes, operacaoElegivel, atualizarOperacao} = await import(`data:text/javascript;base64,${Buffer.from(codigo).toString('base64')}`)
const operacao = (id = '1', statusOperacao = 'PENDENTE', protocoloCentral = 'central') => ({
  id, statusOperacao, protocoloCentral, beneficiarioId: 'BEN-1', statusIntegracao: 'ENVIADA', dataCriacao: '2026-11-14T12:00:00Z',
})
const drenar = async () => {for (let i = 0; i < 15; i++) await Promise.resolve()}
function preparar(t, opcoes = {}) {
  t.mock.timers.enable({apis: ['setTimeout']})
  const controle = new AbortController()
  t.after(() => controle.abort())
  const atualizadas = [], conclusoes = [], chamadas = []
  iniciarPollingOperacoes({operacoes: [operacao()], signal: controle.signal,
    sincronizar: async id => {chamadas.push(id); return operacao(id, 'CONFIRMADA')},
    aoAtualizar: valor => atualizadas.push(valor), aoConcluir: valor => conclusoes.push(valor), ...opcoes})
  return {controle, atualizadas, conclusoes, chamadas}
}

test('elegibilidade exige protocolo e PENDENTE ou nulo, independentemente da integração', () => {
  for (const status of ['PENDENTE', null]) {
    assert.equal(operacaoElegivel(operacao('1', status)), true)
    assert.equal(operacaoElegivel({...operacao('1', status), statusIntegracao: 'ENVIO_INDETERMINADO'}), true)
    assert.equal(operacaoElegivel(operacao('1', status, null)), false)
    assert.equal(operacaoElegivel(operacao('1', status, '')), false)
  }
  for (const status of ['CONFIRMADA', 'REJEITADA', 'FALHA_TECNICA']) assert.equal(operacaoElegivel(operacao('1', status)), false)
})

test('atualização por ID preserva ordem, outras linhas, paginação e totais', () => {
  const pagina = {conteudo: [operacao(), operacao('2')], pagina: 3, tamanho: 10, totalElementos: 32, totalPaginas: 4}
  const nova = atualizarOperacao(pagina, operacao('2', 'CONFIRMADA'))
  assert.deepEqual(nova, {...pagina, conteudo: [pagina.conteudo[0], operacao('2', 'CONFIRMADA')]})
  assert.equal(nova.conteudo[0], pagina.conteudo[0])
  assert.equal(pagina.conteudo[1].statusOperacao, 'PENDENTE')
  assert.deepEqual(atualizarOperacao(pagina, operacao('outro')), pagina)
})

test('aguarda 10 segundos, elimina IDs duplicados e para após status final', async t => {
  const estado = preparar(t, {operacoes: [operacao(), operacao()]})
  t.mock.timers.tick(9999)
  assert.deepEqual(estado.chamadas, [])
  t.mock.timers.tick(1)
  await drenar()
  assert.deepEqual(estado.chamadas, ['1'])
  assert.equal(estado.atualizadas[0].statusOperacao, 'CONFIRMADA')
  t.mock.timers.tick(100000)
  await drenar()
  assert.deepEqual(estado.chamadas, ['1'])
})

test('sem elegíveis não agenda consultas', async t => {
  const estado = preparar(t, {operacoes: [operacao('1', 'FALHA_TECNICA'), operacao('2', 'REJEITADA'), operacao('3', null, null)]})
  t.mock.timers.tick(100000)
  await drenar()
  assert.deepEqual(estado.chamadas, [])
})

test('sincronizações são sequenciais e próximo ciclo espera a conclusão mais 10 segundos', async t => {
  const chamadas = []
  let resolver
  const estado = preparar(t, {operacoes: [operacao(), operacao('2')], sincronizar: async id => {
    chamadas.push(id)
    if (chamadas.length === 1) return new Promise(resolve => {resolver = resolve})
    return operacao(id)
  }})
  t.mock.timers.tick(10000)
  await drenar()
  t.mock.timers.tick(60000)
  await drenar()
  assert.deepEqual(chamadas, ['1'])
  resolver(operacao())
  await drenar()
  assert.deepEqual(chamadas, ['1', '2'])
  assert.deepEqual(estado.conclusoes, [false])
  t.mock.timers.tick(9999)
  assert.deepEqual(chamadas, ['1', '2'])
  t.mock.timers.tick(1)
  await drenar()
  assert.deepEqual(chamadas, ['1', '2', '1', '2'])
})

test('falha parcial preserva estado, continua demais operações e recupera no próximo ciclo', async t => {
  let tentativa = 0
  const estado = preparar(t, {operacoes: [operacao(), operacao('2')], sincronizar: async id => {
    if (id === '1' && tentativa++ === 0) throw new TypeError('Failed to fetch')
    return operacao(id, 'CONFIRMADA')
  }})
  t.mock.timers.tick(10000)
  await drenar()
  assert.deepEqual(estado.atualizadas.map(item => item.id), ['2'])
  assert.deepEqual(estado.conclusoes, [true])
  t.mock.timers.tick(10000)
  await drenar()
  assert.deepEqual(estado.atualizadas.map(item => item.id), ['2', '1'])
  assert.deepEqual(estado.conclusoes, [true, false])
})

for (const rejeitar of [false, true]) {
  test(`abort descarta resposta tardia (${rejeitar ? 'erro' : 'sucesso'}) e impede próximas consultas`, async t => {
    let terminar
    const chamadas = []
    const estado = preparar(t, {operacoes: [operacao(), operacao('2')], sincronizar: (id, signal) => {
      chamadas.push(id)
      assert.equal(signal.aborted, false)
      return new Promise((resolve, reject) => {terminar = () => rejeitar ? reject(new DOMException('Cancelado', 'AbortError')) : resolve(operacao(id))})
    }})
    t.mock.timers.tick(10000)
    estado.controle.abort()
    terminar()
    await drenar()
    t.mock.timers.tick(100000)
    await drenar()
    assert.deepEqual(chamadas, ['1'])
    assert.deepEqual(estado.atualizadas, [])
    assert.deepEqual(estado.conclusoes, [])
  })
}

test('troca de página cancela timer antigo e acompanha apenas a nova página', async t => {
  const antiga = preparar(t)
  t.mock.timers.tick(5000)
  antiga.controle.abort()
  const chamadas = []
  iniciarPollingOperacoes({operacoes: [operacao('nova')], signal: new AbortController().signal,
    sincronizar: async id => {chamadas.push(id); return operacao(id, 'CONFIRMADA')},
    aoAtualizar: () => {}, aoConcluir: () => {}})
  t.mock.timers.tick(5000)
  await drenar()
  assert.deepEqual(chamadas, [])
  t.mock.timers.tick(5000)
  await drenar()
  assert.deepEqual(antiga.chamadas, [])
  assert.deepEqual(chamadas, ['nova'])
})

test('AbortError não é informado como falha e resposta de ID divergente não altera linhas', async t => {
  const estado = preparar(t, {sincronizar: async () => {throw new DOMException('Cancelado', 'AbortError')}})
  t.mock.timers.tick(10000)
  await drenar()
  assert.deepEqual(estado.conclusoes, [false])
  assert.deepEqual(estado.atualizadas, [])
  estado.controle.abort()
  const conclusoes = [], atualizadas = []
  const controle = new AbortController()
  t.after(() => controle.abort())
  iniciarPollingOperacoes({operacoes: [operacao()], signal: controle.signal,
    sincronizar: async () => operacao('outro'), aoConcluir: valor => conclusoes.push(valor), aoAtualizar: valor => atualizadas.push(valor)})
  t.mock.timers.tick(10000)
  await drenar()
  assert.deepEqual(conclusoes, [true])
  assert.deepEqual(atualizadas, [])
})

test('resposta da página antiga não sobrescreve a página nova e montagem cancelada não duplica timer', async t => {
  let resolverAntiga
  const antiga = preparar(t, {sincronizar: () => new Promise(resolve => {resolverAntiga = resolve})})
  t.mock.timers.tick(10000)
  antiga.controle.abort()
  const descartado = new AbortController()
  descartado.abort()
  const controle = new AbortController()
  t.after(() => controle.abort())
  const chamadas = [], atualizadas = []
  const opcoes = {operacoes: [operacao('nova')], sincronizar: async id => {
    chamadas.push(id)
    return operacao(id, 'FALHA_TECNICA')
  }, aoAtualizar: valor => atualizadas.push(valor), aoConcluir: () => {}}
  iniciarPollingOperacoes({...opcoes, signal: descartado.signal})
  iniciarPollingOperacoes({...opcoes, signal: controle.signal})
  t.mock.timers.tick(10000)
  await drenar()
  resolverAntiga(operacao('1', 'CONFIRMADA'))
  await drenar()
  t.mock.timers.tick(100000)
  await drenar()
  assert.deepEqual(chamadas, ['nova'])
  assert.deepEqual(atualizadas, [operacao('nova', 'FALHA_TECNICA')])
  assert.deepEqual(antiga.atualizadas, [])
  assert.deepEqual(antiga.conclusoes, [])
})
