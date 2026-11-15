import {rotulosStatus} from './statusOperacao'
import {mensagemErro} from '../api/errosApi'
import {useEffect, useRef, useState, type SubmitEvent} from 'react'
import {Alert, Button, Checkbox, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography} from '@mui/material'
import {consultarAgenda, criarOperacao} from '../api/operacoesApi'
import type {AgendaResposta, CriarOperacaoRequest, OperacaoResposta} from '../types/operacao'

interface Props {
  onCancelar: () => void
  onSucesso: () => void
}

const moeda = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'})

// Montado novamente a cada nova operação; a chave permanece viva nos reenvios.
export function OperacaoForm({onCancelar, onSucesso}: Props) {
  const [beneficiarioId, setBeneficiarioId] = useState('')
  const [agenda, setAgenda] = useState<AgendaResposta | null>(null)
  const [parcelasIds, setParcelasIds] = useState<string[]>([])
  const [contaDestino, setContaDestino] = useState({banco: '', agencia: '', conta: ''})
  const [consultando, setConsultando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState('')
  const [resultado, setResultado] = useState<OperacaoResposta | null>(null)
  const chave = useRef<string | null>(null)
  const envioEmAndamento = useRef(false)
  const consulta = useRef<AbortController | null>(null)
  useEffect(() => () => consulta.current?.abort(), [])

  function alterar() {
    chave.current = null
    setErro('')
  }

  async function consultar() {
    if (!beneficiarioId.trim() || envioEmAndamento.current) return
    consulta.current?.abort()
    const controle = new AbortController()
    consulta.current = controle
    setConsultando(true)
    setAgenda(null)
    if (parcelasIds.length) alterar()
    setParcelasIds([])
    setErro('')
    try {
      const resposta = await consultarAgenda(beneficiarioId.trim(), controle.signal)
      if (!controle.signal.aborted) setAgenda(resposta)
    } catch (falha) {
      if (!controle.signal.aborted) setErro(mensagemErro(falha, 'Não foi possível consultar as parcelas.'))
    } finally {
      if (!controle.signal.aborted) setConsultando(false)
    }
  }

  async function solicitar(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (envioEmAndamento.current || consultando || resultado) return
    if (!agenda || !parcelasIds.length) {
      setErro('Consulte as parcelas e selecione pelo menos uma.')
      return
    }
    if (!beneficiarioId.trim() || Object.values(contaDestino).some(valor => !valor.trim())) {
      setErro('Preencha todos os campos obrigatórios.')
      return
    }
    const dados: CriarOperacaoRequest = {
      beneficiarioId: beneficiarioId.trim(), parcelasIds,
      contaDestino: {banco: contaDestino.banco.trim(), agencia: contaDestino.agencia.trim(), conta: contaDestino.conta.trim()},
    }
    envioEmAndamento.current = true
    setEnviando(true)
    setErro('')
    try {
      chave.current ??= crypto.randomUUID()
      setResultado(await criarOperacao(dados, chave.current))
    } catch (falha) {
      setErro(falha instanceof Error && !(falha instanceof TypeError)
        ? mensagemErro(falha, 'Não foi possível confirmar o recebimento. Consulte a listagem para acompanhar o resultado.')
        : 'Falha de comunicação. A operação pode ter sido criada. Consulte a listagem para acompanhar o resultado.')
    } finally {
      envioEmAndamento.current = false
      setEnviando(false)
    }
  }

  function fechar() {
    if (envioEmAndamento.current) return
    if (resultado) onSucesso()
    else onCancelar()
  }

  const total = agenda?.parcelas.filter(parcela => parcelasIds.includes(parcela.parcelaId))
    .reduce((soma, parcela) => soma + Math.round(parcela.valor * 100), 0) ?? 0

  return (
    <Dialog open onClose={(_, motivo) => {if (motivo === 'escapeKeyDown' && !resultado) fechar()}} fullWidth maxWidth="md" aria-labelledby="titulo-nova-operacao">
      <form onSubmit={solicitar} aria-busy={enviando}>
        <DialogTitle id="titulo-nova-operacao">Nova operação</DialogTitle>
        <DialogContent>
          {resultado ? <Alert severity={resultado.status === 'REJEITADA' || resultado.status === 'FALHA_TECNICA' ? 'error' : 'success'} role="status">
            Solicitação aceita para processamento. Protocolo: {resultado.protocolo}. Status: {rotulosStatus[resultado.status]}.
            A aceitação não significa que a portabilidade foi concluída.
          </Alert> : <Stack spacing={3} sx={{pt: 1}}>
            <Typography variant="h6" component="h2">Beneficiário</Typography>
            <Stack direction={{xs: 'column', sm: 'row'}} spacing={2}>
              <TextField label="Identificador do beneficiário" required autoFocus fullWidth value={beneficiarioId} disabled={enviando}
                onChange={event => {
                  alterar()
                  consulta.current?.abort()
                  setConsultando(false)
                  setBeneficiarioId(event.target.value)
                  setAgenda(null)
                  setParcelasIds([])
                }} />
              <Button type="button" onClick={consultar} disabled={enviando || consultando || !beneficiarioId.trim()}
                startIcon={consultando ? <CircularProgress size={16} /> : undefined} sx={{flexShrink: 0}}>
                {consultando ? 'Consultando...' : 'Consultar parcelas'}
              </Button>
            </Stack>
            <Typography variant="h6" component="h2">Parcelas disponíveis</Typography>
            {agenda ? agenda.parcelas.length ? <TableContainer>
              <Table size="small" aria-label="Parcelas disponíveis">
                <TableHead><TableRow><TableCell>Selecionar</TableCell><TableCell>Identificador</TableCell><TableCell>Convênio</TableCell><TableCell>Valor</TableCell><TableCell>Data do desconto</TableCell></TableRow></TableHead>
                <TableBody>{agenda.parcelas.map(parcela => <TableRow key={parcela.parcelaId}>
                  <TableCell><Checkbox checked={parcelasIds.includes(parcela.parcelaId)} disabled={enviando}
                    slotProps={{input: {'aria-label': `Selecionar parcela ${parcela.parcelaId}`}}}
                    onChange={(_, selecionada) => {
                      alterar()
                      setParcelasIds(ids => selecionada ? [...ids, parcela.parcelaId] : ids.filter(id => id !== parcela.parcelaId))
                    }} /></TableCell>
                  <TableCell>{parcela.parcelaId}</TableCell><TableCell>{parcela.convenioId}</TableCell>
                  <TableCell>{moeda.format(parcela.valor)}</TableCell><TableCell>{parcela.dataDesconto.split('-').reverse().join('/')}</TableCell>
                </TableRow>)}</TableBody>
              </Table>
            </TableContainer> : <Alert severity="info">Nenhuma parcela disponível para este beneficiário.</Alert>
              : <Typography color="text.secondary">Consulte um beneficiário para visualizar as parcelas.</Typography>}
            <Typography aria-live="polite">Total selecionado: {moeda.format(total / 100)}</Typography>
            <Typography variant="h6" component="h2">Conta de destino</Typography>
            <Stack direction={{xs: 'column', sm: 'row'}} spacing={2}>
              {(['banco', 'agencia', 'conta'] as const).map(campo => <TextField key={campo}
                label={{banco: 'Banco', agencia: 'Agência', conta: 'Conta'}[campo]} required fullWidth
                value={contaDestino[campo]} disabled={enviando} slotProps={{htmlInput: {pattern: '.*\\S.*'}}}
                onChange={event => {alterar(); setContaDestino(conta => ({...conta, [campo]: event.target.value}))}} />)}
            </Stack>
            {erro && <Alert severity="error">{erro}</Alert>}
          </Stack>}
        </DialogContent>
        <DialogActions sx={{p: 3, gap: 1, flexWrap: 'wrap'}}>
          {resultado ? <Button variant="contained" onClick={fechar}>Confirmar</Button> : <>
            <Button disabled={enviando} onClick={fechar}>Cancelar</Button>
            <Button variant="contained" type="submit" disabled={enviando || consultando}
              startIcon={enviando ? <CircularProgress size={16} color="inherit" /> : undefined}>
              {enviando ? 'Enviando...' : 'Solicitar portabilidade'}
            </Button>
          </>}
        </DialogActions>
      </form>
    </Dialog>
  )
}
