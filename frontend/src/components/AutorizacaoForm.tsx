import {useState} from 'react'
import type {SubmitEvent} from 'react'
import {Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Stack, TextField} from '@mui/material'
import {criarAutorizacao} from '../api/autorizacoesApi'

interface Props {
  aberto: boolean
  onCancelar: () => void
  onSucesso: () => void
}

export function AutorizacaoForm({aberto, onCancelar, onSucesso}: Props) {
  const [beneficiarioId, setBeneficiarioId] = useState('')
  const [convenioId, setConvenioId] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState('')

  function cancelar() {
    if (carregando) return
    setErro('')
    onCancelar()
  }

  async function salvar(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (carregando) return
    setCarregando(true)
    setErro('')
    try {
      await criarAutorizacao({beneficiarioId: beneficiarioId.trim(), convenioId: convenioId.trim()})
      setBeneficiarioId('')
      setConvenioId('')
      onSucesso()
    } catch {
      setErro('Não foi possível registrar a autorização. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <Dialog
      open={aberto}
      onClose={(_, motivo) => {if (motivo === 'escapeKeyDown') cancelar()}}
      fullWidth
      maxWidth="xs"
      aria-labelledby="titulo-modal"
      aria-describedby="descricao-modal"
    >
      <form onSubmit={salvar} aria-busy={carregando}>
        <DialogTitle id="titulo-modal">Nova autorização</DialogTitle>
        <DialogContent>
          <DialogContentText id="descricao-modal" sx={{mb: 3}}>Informe os identificadores para autorizar o desconto em folha.</DialogContentText>
          <Stack spacing={3}>
            <TextField id="beneficiario" label="Beneficiário" autoFocus value={beneficiarioId} onChange={e => setBeneficiarioId(e.target.value)} required disabled={carregando} slotProps={{htmlInput: {maxLength: 100, pattern: '.*\\S.*'}}} />
            <TextField id="convenio" label="Convênio" value={convenioId} onChange={e => setConvenioId(e.target.value)} required disabled={carregando} slotProps={{htmlInput: {maxLength: 100, pattern: '.*\\S.*'}}} />
            {erro && <Alert severity="error">{erro}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions sx={{p: 3, pt: 1, flexWrap: 'wrap', gap: 1}}>
          <Button type="button" disabled={carregando} onClick={cancelar}>Cancelar</Button>
          <Button variant="contained" type="submit" disabled={carregando} startIcon={carregando ? <CircularProgress size={16} color="inherit" /> : undefined}>{carregando ? 'Salvando...' : 'Salvar autorização'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
