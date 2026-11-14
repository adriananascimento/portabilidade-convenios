import {useEffect, useState} from 'react'
import {Alert, Box, Button, Chip, Stack, Typography} from '@mui/material'
import {createTheme, ThemeProvider} from '@mui/material/styles'
import {DataGrid, type GridColDef, type GridPaginationModel} from '@mui/x-data-grid'
import {ptBR} from '@mui/x-data-grid/locales'
import {OperacaoForm} from '../components/OperacaoForm'
import {listarOperacoes} from '../api/operacoesApi'
import type {OperacaoListagem, OperacoesPagina, StatusIntegracao, StatusOperacao} from '../types/operacao'

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

const coresStatus: Record<StatusOperacao, 'warning' | 'success' | 'error'> = {
  PENDENTE: 'warning', CONFIRMADA: 'success', REJEITADA: 'error', FALHA_TECNICA: 'error',
}
const rotulosStatus: Record<StatusOperacao, string> = {
  PENDENTE: 'Pendente', CONFIRMADA: 'Confirmada', REJEITADA: 'Rejeitada', FALHA_TECNICA: 'Falha técnica',
}
const integracoes: Record<StatusIntegracao, {rotulo: string; cor: 'warning' | 'success' | 'error'}> = {
  PENDENTE_ENVIO: {rotulo: 'Pendente de envio', cor: 'warning'},
  ENVIADA: {rotulo: 'Enviada', cor: 'success'},
  FALHA_ENVIO: {rotulo: 'Falha no envio', cor: 'error'},
  ENVIO_INDETERMINADO: {rotulo: 'Envio indeterminado', cor: 'warning'},
}
const colunas: GridColDef<OperacaoListagem>[] = [
  {
    field: 'protocoloCentral', headerName: 'Protocolo na Central', flex: 1.5, minWidth: 310,
    valueFormatter: valor => valor ?? 'Não informado',
  },
  {field: 'beneficiarioId', headerName: 'Beneficiário', flex: 1, minWidth: 160},
  {
    field: 'dataCriacao', headerName: 'Data de criação', flex: 1, minWidth: 190,
    valueFormatter: valor => new Date(valor as string).toLocaleString('pt-BR'),
  },
  {
    field: 'statusIntegracao', headerName: 'Status da integração', minWidth: 200,
    renderCell: ({row}) => <Chip label={integracoes[row.statusIntegracao].rotulo} size="small"
      color={integracoes[row.statusIntegracao].cor} variant="outlined" />,
  },
  {
    field: 'statusOperacao', headerName: 'Status da operação', minWidth: 180,
    renderCell: ({row}) => <Chip label={row.statusOperacao === null
      ? row.statusIntegracao === 'PENDENTE_ENVIO' ? 'Aguardando envio' : 'Não informado'
      : rotulosStatus[row.statusOperacao]} size="small"
      color={row.statusOperacao === null ? 'default' : coresStatus[row.statusOperacao]} variant="outlined" />,
  },
  {field: 'acoes', headerName: 'Ações', minWidth: 100, renderCell: () => null},
]
const identificarOperacao = (operacao: OperacaoListagem) => operacao.id

export function Operacoes() {
  const [formularioAberto, setFormularioAberto] = useState(false)
  const [paginacao, setPaginacao] = useState<GridPaginationModel>({page: 0, pageSize: 10})
  const [revisao, setRevisao] = useState(0)
  const [dados, setDados] = useState<OperacoesPagina | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')

  useEffect(() => {
    const controle = new AbortController()
    listarOperacoes(paginacao.page, paginacao.pageSize, controle.signal).then(resposta => {
      if (controle.signal.aborted) return
      setDados(resposta)
      setErro('')
      setCarregando(false)
    }).catch(() => {
      if (controle.signal.aborted) return
      setErro('Não foi possível carregar as operações. Tente novamente.')
      setCarregando(false)
    })
    return () => controle.abort()
  }, [paginacao, revisao])

  function atualizar(modelo = paginacao) {
    setCarregando(true)
    setErro('')
    setPaginacao(modelo.pageSize === paginacao.pageSize ? modelo : {...modelo, page: 0})
    setRevisao(valor => valor + 1)
  }

  return (
    <ThemeProvider theme={tema}>
      <section className="pagina" aria-labelledby="titulo-operacoes">
        <Stack direction={{xs: 'column', md: 'row'}}
               sx={{justifyContent: 'space-between', alignItems: {xs: 'stretch', md: 'center'}, gap: 3, mb: 4}}>
          <div>
            <h1 id="titulo-operacoes">Operações de Portabilidade</h1>
            <p>Consulte e acompanhe as operações de portabilidade de convênios de desconto em folha.</p>
          </div>
          <Button variant="contained" onClick={() => setFormularioAberto(true)} sx={{flexShrink: 0}}>Nova operação</Button>
        </Stack>

        {erro && <Alert severity="error" sx={{mb: 2}} action={<Button color="inherit" onClick={() => atualizar()}>Tentar novamente</Button>}>{erro}</Alert>}

        <Typography variant="h6" component="h2" color="primary" sx={{mb: 2}}>Operações cadastradas</Typography>
        <Box sx={{height: 620, width: '100%'}}>
          <DataGrid
            aria-label="Operações realizadas"
            rows={erro ? [] : dados?.conteudo ?? []}
            columns={colunas}
            getRowId={identificarOperacao}
            loading={carregando}
            rowCount={dados?.totalElementos ?? 0}
            paginationMode="server"
            paginationModel={paginacao}
            onPaginationModelChange={modelo => atualizar(modelo)}
            pageSizeOptions={[10, 25, 50, 100]}
            disableColumnSorting
            disableColumnFilter
            disableColumnMenu
            disableRowSelectionOnClick
            localeText={{...ptBR.components.MuiDataGrid.defaultProps.localeText, noRowsLabel: erro ? 'Listagem indisponível.' : 'Nenhuma operação encontrada nesta página.'}}
            sx={{'& .MuiDataGrid-columnHeaders': {'--DataGrid-containerBackground': '#f4f7fb'}}}
          />
        </Box>
        {formularioAberto && <OperacaoForm onCancelar={() => setFormularioAberto(false)} onSucesso={() => {
          setFormularioAberto(false)
          atualizar({...paginacao, page: 0})
        }} />}
      </section>
    </ThemeProvider>
  )
}
