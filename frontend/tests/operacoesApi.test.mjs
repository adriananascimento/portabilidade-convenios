import {afterEach, test} from 'node:test'
import assert from 'node:assert/strict'
import './registrarTypescript.mjs'
const {consultarAgenda, criarOperacao, listarOperacoes, sincronizarStatusOperacao, consultarEventos} = await import('../src/api/operacoesApi.ts')
const fetchOriginal = globalThis.fetch
afterEach(() => {globalThis.fetch = fetchOriginal})
const dados = {beneficiarioId: '123', parcelasIds: ['p1', 'p2'], contaDestino: {banco: '001', agencia: '02', conta: '003'}}

test('consulta agenda com identificador codificado, sinal e parcelas do contrato', async () => {
  const agenda = {beneficiarioId: '123/a', parcelas: [{parcelaId: 'p1', convenioId: 'c1', valor: 12.5, dataDesconto: '2026-12-10'}]}
  const controle = new AbortController()
  globalThis.fetch = async (url, opcoes) => {
    assert.equal(url, '/api/beneficiarios/123%2Fa/agenda')
    assert.equal(opcoes.signal, controle.signal)
    return Response.json(agenda)
  }
  assert.deepEqual(await consultarAgenda('123/a', controle.signal), agenda)
})

test('agenda vazia e beneficiário inexistente', async () => {
  globalThis.fetch = async () => Response.json({beneficiarioId: '123', parcelas: []})
  assert.deepEqual((await consultarAgenda('123', new AbortController().signal)).parcelas, [])
  globalThis.fetch = async () => new Response(null, {status: 404})
  await assert.rejects(consultarAgenda('123', new AbortController().signal), /Beneficiário não encontrado/)
})

test('reenvio após falha de rede preserva UUID e payload e aceita HTTP 202 pendente', async () => {
  const chave = crypto.randomUUID()
  let chamadas = 0
  const resultado = {protocolo: crypto.randomUUID(), status: 'PENDENTE', beneficiarioId: '123', dataCriacao: '2026-11-14T10:00:00Z'}
  globalThis.fetch = async (url, opcoes) => {
    assert.equal(url, '/api/operacoes')
    assert.equal(opcoes.method, 'POST')
    assert.equal(opcoes.headers['Idempotency-Key'], chave)
    assert.equal(opcoes.headers['Content-Type'], 'application/json')
    assert.deepEqual(JSON.parse(opcoes.body), dados)
    if (++chamadas === 1) throw new TypeError('Failed to fetch')
    return Response.json(resultado, {status: 202})
  }
  await assert.rejects(criarOperacao(dados, chave), /Failed to fetch/)
  assert.deepEqual(await criarOperacao(dados, chave), resultado)
  assert.equal(chamadas, 2)
})

for (const [status, mensagem] of [[400, /inválida/], [409, /Conflito/], [503, /pode ter sido criada/]]) {
  test(`criação HTTP ${status} não informa sucesso`, async () => {
    globalThis.fetch = async () => new Response(null, {status})
    await assert.rejects(criarOperacao(dados, crypto.randomUUID()), mensagem)
  })
}


test('listagem preserva paginação, IDs locais e operações sem protocolo ou com falha de integração', async () => {
  const pagina = {
    conteudo: ['PENDENTE_ENVIO', 'FALHA_ENVIO', 'ENVIO_INDETERMINADO', 'ENVIADA'].map(statusIntegracao => ({
      id: crypto.randomUUID(), beneficiarioId: 'BEN-1', dataCriacao: '2026-11-14T12:00:00Z',
      statusIntegracao, statusOperacao: null, protocoloCentral: null,
    })),
    pagina: 1, tamanho: 10, totalElementos: 14, totalPaginas: 2,
  }
  const controle = new AbortController()
  globalThis.fetch = async (url, opcoes) => {
    assert.equal(url, '/api/operacoes?pagina=1&tamanho=10')
    assert.equal(opcoes.signal, controle.signal)
    return Response.json(pagina)
  }
  assert.deepEqual(await listarOperacoes(1, 10, controle.signal), pagina)
})

test('listagem vazia preserva metadados e falhas HTTP e de rede são propagadas', async () => {
  const pagina = {conteudo: [], pagina: 0, tamanho: 10, totalElementos: 0, totalPaginas: 0}
  const sinal = new AbortController().signal
  globalThis.fetch = async () => Response.json(pagina)
  assert.deepEqual(await listarOperacoes(0, 10, sinal), pagina)
  globalThis.fetch = async () => new Response(null, {status: 503})
  await assert.rejects(listarOperacoes(0, 10, sinal), /Não foi possível listar/)
  globalThis.fetch = async () => {throw new TypeError('Failed to fetch')}
  await assert.rejects(listarOperacoes(0, 10, sinal), /Failed to fetch/)
})



test('sincronização usa POST sem corpo, ID local codificado, sinal e DTO da listagem', async () => {
  const controle = new AbortController()
  const operacao = {id: 'local/a', protocoloCentral: 'central', statusOperacao: 'CONFIRMADA', statusIntegracao: 'ENVIADA', beneficiarioId: 'BEN-1', dataCriacao: '2026-11-14T12:00:00Z'}
  globalThis.fetch = async (url, opcoes) => {
    assert.equal(url, '/api/operacoes/local%2Fa/sincronizar-status')
    assert.deepEqual(opcoes, {method: 'POST', signal: controle.signal})
    return Response.json(operacao)
  }
  assert.deepEqual(await sincronizarStatusOperacao('local/a', controle.signal), operacao)
})

test('sincronização propaga erros HTTP, rede e cancelamento sem inventar status', async () => {
  const sinal = new AbortController().signal
  for (const status of [404, 409, 500, 502, 503, 504]) {
    globalThis.fetch = async () => new Response(null, {status})
    await assert.rejects(sincronizarStatusOperacao('local', sinal), /Não foi possível sincronizar/)
  }
  globalThis.fetch = async () => {throw new TypeError('Failed to fetch')}
  await assert.rejects(sincronizarStatusOperacao('local', sinal), /Failed to fetch/)
  globalThis.fetch = async () => {throw new DOMException('Cancelado', 'AbortError')}
  await assert.rejects(sincronizarStatusOperacao('local', sinal), {name: 'AbortError'})
})


test('histórico usa UUID local codificado, sinal e campos reais, inclusive lista vazia', async () => {
  const controle = new AbortController()
  for (const eventos of [[], [{id: 'e1', tipo: 'PROTOCOLO_RECEBIDO', descricao: 'Protocolo recebido', dataCriacao: '2026-11-15T10:00:00Z', statusIntegracaoAnterior: 'PENDENTE_ENVIO', statusIntegracaoNovo: 'ENVIADA', statusOperacaoAnterior: null, statusOperacaoNovo: 'PENDENTE', protocoloCentral: 'central'}]]) {
    globalThis.fetch = async (url, opcoes) => {
      assert.equal(url, '/api/operacoes/local%2Fa/eventos')
      assert.equal(opcoes.signal, controle.signal)
      return Response.json(eventos)
    }
    assert.deepEqual(await consultarEventos('local/a', controle.signal), eventos)
  }
})

for (const [status, mensagem] of [[400, /inválida/], [401, /autenticação/], [403, /permissão/], [409, /Conflito/], [502, /Portal e a Central/], [503, /indisponível/], [504, /demorou/]]) {
  test(`histórico e autorizações tratam HTTP ${status} sem expor detalhes internos`, async () => {
    const {listarAutorizacoes} = await import('../src/api/autorizacoesApi.ts')
    globalThis.fetch = async () => Response.json({detail: 'java.lang.Exception: secret=123', trace: 'stack trace'}, {status})
    await assert.rejects(consultarEventos('id', new AbortController().signal), mensagem)
    await assert.rejects(listarAutorizacoes(0, 10, new AbortController().signal), mensagem)
  })
}

test('reaproveita mensagem pública real do backend e ignora corpo HTML', async () => {
  const detalhe = 'Falha de autenticação na comunicação com a Central de Convênios.'
  globalThis.fetch = async () => Response.json({detail: detalhe}, {status: 502})
  await assert.rejects(consultarEventos('id', new AbortController().signal), {message: detalhe})
  globalThis.fetch = async () => new Response('<h1>stack trace</h1>', {status: 502})
  await assert.rejects(consultarEventos('id', new AbortController().signal), /Portal e a Central/)
})

test('resposta inválida não exibe erro técnico do parser e rede usa mensagem local', async () => {
  const {mensagemErro} = await import('../src/api/errosApi.ts')
  assert.equal(mensagemErro(new SyntaxError('Unexpected token secret'), 'Resposta indisponível.'), 'Resposta indisponível.')
  assert.match(mensagemErro(new TypeError('Failed to fetch'), 'Falha.'), /Falha de rede/)
})
