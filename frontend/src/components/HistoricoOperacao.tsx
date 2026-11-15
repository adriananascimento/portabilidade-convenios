import {useEffect, useState} from 'react'
import {Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Typography} from '@mui/material'
import {consultarEventos} from '../api/operacoesApi'
import {mensagemErro} from '../api/errosApi'
import type {EventoOperacao, TipoEventoOperacao} from '../types/operacao'
import {integracoes, rotulosStatus} from './statusOperacao'

const tipos: Record<TipoEventoOperacao, string> = {
  OPERACAO_CRIADA_LOCALMENTE: 'Operação registrada',
  TENTATIVA_ENVIO_CENTRAL: 'Tentativa de envio à Central',
  PROTOCOLO_RECEBIDO: 'Protocolo recebido',
  FALHA_INTEGRACAO: 'Falha na integração',
  STATUS_OPERACAO_ATUALIZADO: 'Status da operação atualizado',
  RESULTADO_INDETERMINADO: 'Resultado indeterminado',
}

export function HistoricoOperacao({id, onFechar}: {id: string; onFechar: () => void}) {
  const [eventos, setEventos] = useState<EventoOperacao[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [revisao, setRevisao] = useState(0)

  useEffect(() => {
    const controle = new AbortController()
    consultarEventos(id, controle.signal).then(resposta => {
      if (!controle.signal.aborted) setEventos([...resposta].sort((a, b) => Date.parse(b.dataCriacao) - Date.parse(a.dataCriacao)))
    }).catch((falha: unknown) => {
      if (!controle.signal.aborted) setErro(mensagemErro(falha, 'Não foi possível consultar o histórico.'))
    }).finally(() => {
      if (!controle.signal.aborted) setCarregando(false)
    })
    return () => controle.abort()
  }, [id, revisao])

  return <Dialog open onClose={onFechar} fullWidth maxWidth="sm" aria-labelledby="titulo-historico">
    <DialogTitle id="titulo-historico">Histórico da operação</DialogTitle>
    <DialogContent aria-busy={carregando} sx={{overflowWrap: 'anywhere'}}>
      {carregando ? <Stack direction="row" spacing={2} role="status" sx={{py: 2}}>
        <CircularProgress size={24} aria-label="Carregando histórico" /><Typography>Carregando histórico...</Typography>
      </Stack> : erro ? <Alert severity="error" action={<Button color="inherit" onClick={() => {
        setCarregando(true); setErro(''); setRevisao(valor => valor + 1)
      }}>Tentar novamente</Button>}>{erro}</Alert>
        : eventos.length === 0 ? <Alert severity="info">Nenhum evento registrado para esta operação.</Alert>
          : <Stack component="ol" spacing={2} sx={{pl: 3}}>{eventos.map(evento => <Stack component="li" key={evento.id}
            spacing={1} sx={{borderBottom: '1px solid', borderColor: 'divider', pb: 2}}>
            <Typography component="h3" variant="subtitle1" sx={{fontWeight: 600}}>{tipos[evento.tipo] ?? 'Evento da operação'}</Typography>
            <Typography component="time" dateTime={evento.dataCriacao} variant="body2" color="text.secondary">
              {new Date(evento.dataCriacao).toLocaleString('pt-BR')}
            </Typography>
            {evento.descricao && <Typography>{evento.descricao}</Typography>}
            {(evento.statusIntegracaoAnterior || evento.statusIntegracaoNovo) && <Typography variant="body2">
              Integração: {evento.statusIntegracaoAnterior ? integracoes[evento.statusIntegracaoAnterior]?.rotulo ?? 'Não informado' : 'Não informado'}
              {' → '}{evento.statusIntegracaoNovo ? integracoes[evento.statusIntegracaoNovo]?.rotulo ?? 'Não informado' : 'Não informado'}
            </Typography>}
            {(evento.statusOperacaoAnterior || evento.statusOperacaoNovo) && <Typography variant="body2">
              Operação: {evento.statusOperacaoAnterior ? rotulosStatus[evento.statusOperacaoAnterior] ?? 'Não informado' : 'Não informado'}
              {' → '}{evento.statusOperacaoNovo ? rotulosStatus[evento.statusOperacaoNovo] ?? 'Não informado' : 'Não informado'}
            </Typography>}
          </Stack>)}</Stack>}
    </DialogContent>
    <DialogActions sx={{p: 3}}><Button onClick={onFechar} autoFocus>Fechar</Button></DialogActions>
  </Dialog>
}
