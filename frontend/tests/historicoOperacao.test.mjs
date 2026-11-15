import {afterEach, test} from 'node:test'
import assert from 'node:assert/strict'
import {JSDOM} from 'jsdom'
import './registrarTypescript.mjs'

const dom = new JSDOM('<!doctype html><html><body></body></html>', {url: 'http://localhost'})
for (const nome of ['window', 'document', 'HTMLElement', 'Element', 'Node', 'DocumentFragment', 'MutationObserver', 'getComputedStyle']) {
  globalThis[nome] = nome === 'getComputedStyle' ? dom.window.getComputedStyle.bind(dom.window) : dom.window[nome]
}
Object.defineProperty(globalThis, 'navigator', {value: dom.window.navigator, configurable: true})
globalThis.IS_REACT_ACT_ENVIRONMENT = true
const {createElement: h} = await import('react')
const {render, screen, fireEvent, waitFor, cleanup} = await import('@testing-library/react')
const {HistoricoOperacao} = await import('../src/components/HistoricoOperacao.tsx')
const {Operacoes} = await import('../src/pages/Operacoes.tsx')
const originalFetch = globalThis.fetch
afterEach(() => {cleanup(); globalThis.fetch = originalFetch})
const evento = (id, tipo, dataCriacao, outros = {}) => ({id, tipo, dataCriacao, descricao: null,
  statusIntegracaoAnterior: null, statusIntegracaoNovo: null, statusOperacaoAnterior: null, statusOperacaoNovo: null,
  protocoloCentral: null, ...outros})
const operacao = (id, statusIntegracao = 'ENVIADA', statusOperacao = 'PENDENTE') => ({
  id, beneficiarioId: 'BEN-1', dataCriacao: '2026-11-15T10:00:00Z', statusIntegracao, statusOperacao, protocoloCentral: 'central',
})
const pagina = conteudo => ({conteudo, pagina: 0, tamanho: 10, totalElementos: conteudo.length, totalPaginas: 1})

test('abre pela listagem, consulta uma vez, fecha pelo botão e cancela a consulta', async () => {
  let consultas = 0, sinal
  globalThis.fetch = async (url, opcoes) => {
    if (url.includes('/eventos')) {consultas++; sinal = opcoes.signal; return Response.json([])}
    return Response.json(pagina([operacao('local')]))
  }
  render(h(Operacoes))
  fireEvent.click(await screen.findByRole('button', {name: 'Ver histórico da operação local'}))
  assert.ok(await screen.findByRole('dialog', {name: 'Histórico da operação'}))
  await screen.findByText('Nenhum evento registrado para esta operação.')
  assert.equal(consultas, 1)
  fireEvent.click(screen.getByRole('button', {name: 'Fechar'}))
  await waitFor(() => assert.equal(screen.queryByRole('dialog'), null))
  assert.equal(sinal.aborted, true)
})

test('exibe descrição, data pt-BR, nomes amigáveis e transições em ordem decrescente', async () => {
  globalThis.fetch = async () => Response.json([
    evento('1', 'OPERACAO_CRIADA_LOCALMENTE', '2026-11-14T10:00:00Z'),
    evento('2', 'STATUS_OPERACAO_ATUALIZADO', '2026-11-15T11:00:00Z', {descricao: 'Resultado recebido da Central',
      statusIntegracaoAnterior: 'PENDENTE_ENVIO', statusIntegracaoNovo: 'ENVIADA',
      statusOperacaoAnterior: 'PENDENTE', statusOperacaoNovo: 'CONFIRMADA'}),
  ])
  render(h(HistoricoOperacao, {id: 'local', onFechar: () => {}}))
  await screen.findByText('Resultado recebido da Central')
  assert.deepEqual(screen.getAllByRole('heading', {level: 3}).map(el => el.textContent), ['Status da operação atualizado', 'Operação registrada'])
  assert.ok(screen.getByText('Integração: Pendente de envio → Enviada'))
  assert.ok(screen.getByText('Operação: Pendente → Confirmada'))
  assert.ok(screen.getByText(new Date('2026-11-15T11:00:00Z').toLocaleString('pt-BR')))
})

test('carregamento e cancelamento ao desmontar descartam resposta tardia', async () => {
  let sinal, resolver
  globalThis.fetch = (_, opcoes) => {sinal = opcoes.signal; return new Promise(resolve => {resolver = resolve})}
  const {unmount} = render(h(HistoricoOperacao, {id: 'local', onFechar: () => {}}))
  assert.ok(screen.getByRole('status'))
  assert.ok(screen.getByLabelText('Carregando histórico'))
  unmount()
  assert.equal(sinal.aborted, true)
  resolver(Response.json([]))
  await Promise.resolve()
  assert.equal(screen.queryByRole('dialog'), null)
})

test('falha HTTP permite nova tentativa sem mostrar detalhes internos e depois exibe vazio', async () => {
  let chamadas = 0
  globalThis.fetch = async () => ++chamadas === 1
    ? Response.json({detail: 'java.lang.Exception secret=abc'}, {status: 502}) : Response.json([])
  render(h(HistoricoOperacao, {id: 'local', onFechar: () => {}}))
  const erro = await screen.findByRole('alert')
  assert.match(erro.textContent, /Portal e a Central/)
  assert.doesNotMatch(erro.textContent, /Exception|secret/)
  fireEvent.click(screen.getByRole('button', {name: 'Tentar novamente'}))
  await screen.findByText('Nenhum evento registrado para esta operação.')
  assert.equal(chamadas, 2)
})

test('falha de rede tem mensagem clara e Escape fecha o modal', async () => {
  globalThis.fetch = async () => {throw new TypeError('Failed to fetch')}
  let fechado = false
  render(h(HistoricoOperacao, {id: 'local', onFechar: () => {fechado = true}}))
  assert.match((await screen.findByRole('alert')).textContent, /Falha de rede/)
  fireEvent.keyDown(screen.getByRole('dialog'), {key: 'Escape', code: 'Escape'})
  assert.equal(fechado, true)
})

test('listagem identifica todos os status de integração e operação, incluindo nulos', async () => {
  globalThis.fetch = async () => Response.json(pagina([
    operacao('1', 'PENDENTE_ENVIO', null), operacao('2', 'FALHA_ENVIO', 'REJEITADA'),
    operacao('3', 'ENVIO_INDETERMINADO', 'FALHA_TECNICA'), operacao('4', 'ENVIADA', 'CONFIRMADA'),
    operacao('5', 'ENVIADA', 'PENDENTE'), operacao('6', 'FALHA_ENVIO', null),
  ]))
  render(h(Operacoes))
  await screen.findByText('Aguardando envio')
  for (const rotulo of ['Pendente de envio', 'Falha no envio', 'Envio indeterminado', 'Enviada', 'Rejeitada', 'Falha técnica', 'Confirmada', 'Pendente', 'Não informado']) {
    assert.ok(screen.getAllByText(rotulo).length > 0)
  }
  for (const rotulo of ['Rejeitada', 'Falha técnica']) assert.match(screen.getByText(rotulo).parentElement.className, /MuiChip-colorError/)
})

test('falha de integração na criação preserva campos e seleção sem informar sucesso', async () => {
  const {OperacaoForm} = await import('../src/components/OperacaoForm.tsx')
  let envios = 0
  globalThis.fetch = async url => {
    if (url.includes('/agenda')) return Response.json({beneficiarioId: 'BEN-1', parcelas: [{parcelaId: 'p1', convenioId: 'c1', valor: 10, dataDesconto: '2026-12-10'}]})
    envios++
    return Response.json({detail: 'Falha de autenticação na comunicação com a Central de Convênios.'}, {status: 502})
  }
  render(h(OperacaoForm, {onCancelar: () => {}, onSucesso: () => {assert.fail('Falha não pode informar sucesso')}}))
  fireEvent.change(screen.getByLabelText(/Identificador do beneficiário/), {target: {value: 'BEN-1'}})
  fireEvent.click(screen.getByRole('button', {name: 'Consultar parcelas'}))
  fireEvent.click(await screen.findByRole('checkbox', {name: 'Selecionar parcela p1'}))
  for (const [campo, valor] of [['Banco', '001'], ['Agência', '02'], ['Conta', '003']]) {
    fireEvent.change(screen.getByLabelText(new RegExp(campo)), {target: {value: valor}})
  }
  fireEvent.click(screen.getByRole('button', {name: 'Solicitar portabilidade'}))
  assert.match((await screen.findByRole('alert')).textContent, /Falha de autenticação/)
  assert.equal(screen.getByLabelText(/Identificador do beneficiário/).value, 'BEN-1')
  assert.equal(screen.getByLabelText(/Banco/).value, '001')
  assert.equal(screen.getByLabelText(/Agência/).value, '02')
  assert.equal(screen.getByLabelText(/Conta/).value, '003')
  assert.equal(screen.getByRole('checkbox', {name: 'Selecionar parcela p1'}).checked, true)
  assert.equal(envios, 1)
  assert.equal(screen.queryByText(/Solicitação aceita/), null)
})

test('polling da tela mantém dados após falha e desmontagem cancela próximo ciclo', async t => {
  const {act} = await import('@testing-library/react')
  const agendados = new Map()
  const timeoutOriginal = globalThis.setTimeout
  const clearOriginal = globalThis.clearTimeout
  t.mock.method(globalThis, 'setTimeout', (callback, intervalo, ...args) => {
    if (intervalo !== 10000) return timeoutOriginal(callback, intervalo, ...args)
    const id = Symbol('polling')
    agendados.set(id, callback)
    return id
  })
  t.mock.method(globalThis, 'clearTimeout', id => {
    if (!agendados.delete(id)) clearOriginal(id)
  })
  let consultas = 0, sinal
  globalThis.fetch = async (url, opcoes) => {
    if (url.includes('/sincronizar-status')) {
      consultas++; sinal = opcoes.signal
      return new Response(null, {status: 503})
    }
    return Response.json(pagina([operacao('local')]))
  }
  const {unmount} = render(h(Operacoes))
  await screen.findByText('Pendente')
  assert.equal(agendados.size, 1)
  const [id, callback] = [...agendados][0]
  agendados.delete(id)
  await act(async () => {callback(); for (let i = 0; i < 15; i++) await Promise.resolve()})
  assert.equal(consultas, 1)
  assert.ok(screen.getByText('Pendente'))
  assert.match(screen.getByRole('alert').textContent, /dados conhecidos foram mantidos/)
  assert.equal(agendados.size, 1)
  unmount()
  assert.equal(sinal.aborted, true)
  assert.equal(agendados.size, 0)
})
