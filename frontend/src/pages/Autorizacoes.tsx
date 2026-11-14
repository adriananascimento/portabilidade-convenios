import {useEffect, useState} from 'react'
import {Alert, Box, Button, Chip, Stack, Typography} from '@mui/material'
import {createTheme, ThemeProvider} from '@mui/material/styles'
import {DataGrid, type GridColDef, type GridPaginationModel} from '@mui/x-data-grid'
import {ptBR} from '@mui/x-data-grid/locales'
import {AutorizacaoForm} from '../components/AutorizacaoForm'
import {listarAutorizacoes} from '../api/autorizacoesApi'
import type {AutorizacaoResposta, AutorizacoesPagina} from '../types/autorizacao'

const tema = createTheme({
  palette: {
    primary: {main: '#24548d'},
    text: {primary: '#1c304b', secondary: '#586a81'},
    divider: '#e1e8f1',
    success: {main: '#186346'},
  },
  typography: {fontFamily: "Inter, 'Segoe UI', Roboto, Arial, sans-serif", button: {textTransform: 'none'}},
  shape: {borderRadius: 8},
})

const colunas: GridColDef<AutorizacaoResposta>[] = [
  {field: 'beneficiarioId', headerName: 'Beneficiário', flex: 1, minWidth: 160},
  {field: 'convenioId', headerName: 'Convênio', flex: 1, minWidth: 160},
  {
    field: 'dataAutorizacao', headerName: 'Data da autorização', flex: 1, minWidth: 190,
    valueFormatter: valor => new Date(valor as string).toLocaleString('pt-BR'),
  },
  {
    field: 'status', headerName: 'Status', minWidth: 130,
    renderCell: ({row}) => <Chip label={row.status} size="small" color={row.status === 'ATIVA' ? 'success' : 'default'} variant="outlined" />,
  },
]
const identificarAutorizacao = (autorizacao: AutorizacaoResposta) => autorizacao.autorizacaoId

export function Autorizacoes() {
  const [paginacao, setPaginacao] = useState<GridPaginationModel>({page: 0, pageSize: 10})
  const [revisao, setRevisao] = useState(0)
  const [dados, setDados] = useState<AutorizacoesPagina | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState('')
  const [modalAberto, setModalAberto] = useState(false)

  useEffect(() => {
    const controle = new AbortController()
    listarAutorizacoes(paginacao.page, paginacao.pageSize, controle.signal).then(resposta => {
      if (controle.signal.aborted) return
      setDados(resposta)
      setErro('')
      setCarregando(false)
    }).catch(() => {
      if (!controle.signal.aborted) {
        setErro('Não foi possível carregar as autorizações. Tente novamente.')
        setCarregando(false)
      }
    })
    return () => controle.abort()
  }, [paginacao, revisao])

  function atualizar(novaPaginacao = paginacao) {
    setCarregando(true)
    setErro('')
    setPaginacao(novaPaginacao)
    setRevisao(valor => valor + 1)
  }

  return (
    <ThemeProvider theme={tema}>
      <section className="pagina">
        <Stack
          direction={{xs: 'column', md: 'row'}}
          sx={{justifyContent: 'space-between', alignItems: {xs: 'stretch', md: 'center'}, gap: 3, mb: 4}}
        >
          <div>
            <h1>Autorizações</h1>
            <p>Gerencie as autorizações de desconto em folha dos beneficiários.</p>
          </div>
          <Button
            variant="contained"
            onClick={() => setModalAberto(true)} sx={{flexShrink: 0}}
          >+ Nova autorização
          </Button>
        </Stack>

        {sucesso && <Alert severity="success" role="status" sx={{mb: 2}}>{sucesso}</Alert>}

        {erro && <Alert severity="error" sx={{mb: 2}} action={<Button color="inherit" onClick={() => atualizar()}>Tentar novamente</Button>}>{erro}</Alert>}

        <Stack direction={{xs: 'column', sm: 'row'}} sx={{justifyContent: 'space-between', gap: 1, mb: 2}}>
          <Typography variant="h6" component="h2" color="primary">Autorizações cadastradas</Typography>
        </Stack>

        <Box sx={{height: 620, width: '100%'}}>
          <DataGrid
            aria-label="Autorizações cadastradas"
            rows={erro ? [] : dados?.conteudo ?? []}
            columns={colunas}
            getRowId={identificarAutorizacao}
            loading={carregando}
            rowCount={dados?.totalElementos ?? 0}
            paginationMode="server"
            paginationModel={paginacao}
            onPaginationModelChange={modelo => atualizar(modelo)}
            pageSizeOptions={[10]}
            disableColumnSorting
            disableColumnFilter
            disableColumnMenu
            disableRowSelectionOnClick
            localeText={{...ptBR.components.MuiDataGrid.defaultProps.localeText, noRowsLabel: erro ? 'Listagem indisponível.' : 'Nenhuma autorização encontrada nesta página.'}}
            sx={{'& .MuiDataGrid-columnHeaders': {'--DataGrid-containerBackground': '#f4f7fb'}}}
          />
        </Box>
        <AutorizacaoForm aberto={modalAberto} onCancelar={() => setModalAberto(false)} onSucesso={() => {
          setModalAberto(false)
          setSucesso('Autorização registrada com sucesso!')
          atualizar({...paginacao, page: 0})
        }} />
      </section>
    </ThemeProvider>
  )
}
