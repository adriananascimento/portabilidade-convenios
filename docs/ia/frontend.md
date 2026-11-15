# Uso de IA — Frontend

O frontend foi desenvolvido de forma incremental, com o Codex como ferramenta de apoio à análise dos contratos, implementação, testes e documentação.
A desenvolvedora definiu o escopo e as restrições das etapas e avaliou propostas técnicas. Entre as decisões registradas estão a apresentação dos estados de integração, a elegibilidade para o polling, o tratamento de cancelamentos de requisições e a preservação dos dados em situações de erro. O Codex auxiliou na implementação e nas validações dessas orientações.

## Ferramenta utilizada

Codex, nas atividades documentadas abaixo.

## Orientações e restrições

- Preservar a separação entre frontend, backend do Portal e Central de Convênios.
- Utilizar nomenclatura e mensagens em português.
- Respeitar os contratos HTTP e as convenções existentes do projeto.
- Manter as alterações limitadas ao escopo de cada etapa, evitando dependências desnecessárias.
- Apresentar decisões relevantes para avaliação antes da implementação.
- Registrar testes, limitações e problemas encontrados nas validações.

## Atividades e validações

As seções seguintes apresentam as solicitações, decisões registradas, implementações auxiliadas pelo Codex e evidências de validação. As contagens de testes são resultados históricos de cada etapa, não uma medição única do estado atual do projeto.

## Listagem paginada de autorizações

- Tarefa: tabela paginada em /autorizacoes e criação em modal, consistente com
  o layout azul existente, preservando a integração POST já presente no workspace.
- Trabalho do Codex: alteração de Autorizacoes.tsx, AutorizacaoForm.tsx,
  autorizacoesApi.ts, types/autorizacao.ts e App.css. Estados de carregamento,
  erro com nova tentativa, vazio e sucesso; Anterior/Próxima; consulta cancelável
  para evitar resposta antiga sobrescrever página atual; atualização da primeira
  página após salvar. Datas exibidas em pt-BR no fuso do navegador.
- Modal nativo dialog: foco contido pelo navegador, labels, validação de campos,
  Cancelar e Esc; fechamento bloqueado durante envio. Tabela com rolagem horizontal
  em telas estreitas; cabeçalho e paginação se reorganizam no mobile.
- Validações: npm run build --prefix frontend (TypeScript + Vite) e
  npm run lint --prefix frontend — ambos concluídos com sucesso.
- Limitação: não foi realizada inspeção visual nem teste ponta a ponta em navegador.
- Orientações: escopo limitado às autorizações, português, camadas existentes,
  sem bibliotecas novas e sem alterar operações de portabilidade. Alterações locais
  preexistentes foram preservadas.

## Refatoração de Autorizações com Material UI

- Solicitação: simplificar a tela com Material UI e MUI X Data Grid Community,
  preservando contratos HTTP, paginação no servidor, cadastro, identidade visual
  e menu lateral, sem alterar backend ou Central de Convênios.
- Análise: preservados páginas iniciando em zero, tamanho 10, cancelamento de
  consultas antigas, datas em pt-BR no fuso do navegador, validação de campos
  obrigatórios com até 100 caracteres e conteúdo não vazio após trim, bloqueio
  de fechamento durante envio e atualização da primeira página após cadastrar.
- Implementação: instalados @mui/material, @mui/x-data-grid e Emotion, com lockfile.
  DataGrid usa um único paginationModel e totalElementos do servidor. Ordenação
  e filtros locais desabilitados para manter a ordem do backend. Dialog substitui
  dialog/ref; TextField, Button, Alert, Chip e CircularProgress substituem os
  controles e feedbacks manuais. Stack e tema local reutilizam cores e fontes.
  Removido CSS específico da tabela, paginação, modal, botões e mensagens.
- Documentação consultada: https://mui.com/x/react-data-grid/pagination/.
- Validação: npm run build --prefix frontend passou (tsc -b e Vite);
  npm run lint --prefix frontend e git diff --check também passaram.
  O build inicial identificou propriedades incompatíveis com MUI 9; corrigidas
  antes do build final. Vite avisou sobre bundle maior que 500 kB.

## Operações de Portabilidade

- Ferramenta: Codex, para inspeção do contrato Java, implementação e validação.
- Solicitação: primeira versão de `/operacoes` com MUI Data Grid Community,
  paginação no servidor e estados de consulta, seguindo Autorizações.
- Orientações: preservar Autorizações, backend e Central; nenhuma biblioteca
  adicional, criação, formulário, trilha de eventos ou atualização automática.
- Contrato conferido em OperacaoController, OperacaoPaginaDTO e
  OperacaoRespostaDTO: parâmetros `pagina` (zero inicial) e `tamanho` (1–100),
  envelope com `conteudo` e totais; registros só têm `protocolo` e `status`.
- Resultado: cliente HTTP e tipos próprios, protocolo como identificador,
  cancelamento de consultas antigas, tamanhos 10/25/50/100, retorno à página zero
  ao mudar tamanho, loading, vazio, erro e nova tentativa. Status com Chip;
  botão Nova operação desabilitado e coluna Ações sem controles.
- Limitação apresentada ao usuário: beneficiário e data de criação ausentes
  no DTO. Colunas mantidas com “Não informado” e aviso explícito na tela.
  Solicitada preferência à desenvolvedora; nenhuma revisão humana concluída registrada.
- Validações: build (inclui TypeScript) e lint passaram. Script temporário com
  fetch simulado verificou parâmetros, AbortSignal, leitura do DTO, falhas HTTP
  400/500/503 e falha de rede. Não equivale a teste com backend real.
- Integração real: tentativa de GET em localhost:8080 falhou por conexão recusada;
  serviço não disponível. Sem validação visual ou ponta a ponta em navegador.
  Vite avisou sobre bundle acima de 500 kB.

## Formulário de operações de portabilidade

- Solicitação: modal Material UI aberto por Nova operação, consulta de agenda,
  seleção múltipla e total, conta de destino e criação com idempotência.
- Ferramenta: Codex para leitura dos DTOs/controladores Java, implementação e
  validação. Contratos conferidos: AgendaRespostaDTO, ParcelaRespostaDTO,
  CriarOperacaoRequestDTO, OperacaoRespostaDTO e endpoints correspondentes.
- Orientações aplicadas: português, reutilização de MUI/tema, HTTP separado,
  nenhum pacote novo, sem mudanças no backend/Central, polling ou eventos.
- Resultado: OperacaoForm montado a cada abertura; consulta cancelada ao editar
  beneficiário ou desmontar, seleção limpa, campos obrigatórios, estados de erro,
  carregamento e aceite com protocolo/status. Confirmação fecha e atualiza página
  zero. HTTP 202 não é apresentado como conclusão da portabilidade.
- Idempotência: crypto.randomUUID no primeiro envio, ref preservada após erros;
  alterações de dados invalidam a chave e próxima solicitação gera outra.
  Ref de envio bloqueia duplicidade e fechamento; controles ficam desabilitados.
  A chave não persiste ao fechar o modal ou recarregar a página.
- Validações: npm run build --prefix frontend (inclui TypeScript) e
  npm run lint --prefix frontend passaram. Vite avisou bundle acima de 500 kB.
  node --test frontend/tests/operacoesApi.test.mjs: seis testes passaram com fetch
  simulado, cobrindo agenda, URI codificada, sinal, agenda vazia, 404, payload e
  UUID preservados pelo cliente em reenvio após falha de rede, aceite 202/PENDENTE
  e erros 400/409/503. Esses testes não exercitam interações do componente.
- Limitações: GET localhost:8080 falhou por conexão recusada (HTTP 000); sem
  integração real, inspeção visual ou teste ponta a ponta em navegador.
  Revisão humana ainda não registrada para esta implementação.

## Polling de status das operações

- Ferramenta: Codex para análise do React, serviço Java, contrato HTTP e ADRs,
  implementação do frontend, testes e documentação.
- Aprovação humana: proposta aprovada antes das alterações. A desenvolvedora ajustou
  a elegibilidade para protocolo presente e status PENDENTE ou nulo; determinou
  que FALHA_TECNICA encerre apenas o polling automático e que aborts não apareçam
  como erros. Backend e Central não devem ser alterados.
- Resultado: cliente POST sem corpo com UUID local e AbortSignal; controlador
  com setTimeout recursivo de 10 segundos, consultas sequenciais e IDs únicos.
  A tela inicia acompanhamento após a carga da página e cancela na nova carga,
  troca de página e desmontagem. Respostas antigas são descartadas. Atualizações
  por ID preservam paginação, totais, ordem e formulário. Falhas preservam dados
  conhecidos e exibem aviso, com novas tentativas nos ciclos seguintes.
- Testes: node:test e temporizadores simulados, sem novas dependências, cobrindo
  contrato HTTP, elegibilidade, exclusão de FALHA_TECNICA, espera, sequência,
  ausência de sobreposição, IDs duplicados, falhas parciais, recuperação,
  cancelamento, respostas tardias, troca de contexto e atualização por ID.
- Validação: build TypeScript/Vite e lint passaram. Build mantém aviso de chunk
  acima de 500 kB. Resultado final dos testes e git diff --check registrado abaixo.
- Limitações: testes exercitam cliente HTTP e controlador, sem montar React,
  validação visual ou integração real com o backend. Múltiplas abas não são
  coordenadas; abort não garante cancelamento do trabalho remoto.
- Revisão humana: aprovação do escopo registrada; revisão final da implementação
  ainda não registrada.
- Resultado final: `node --test frontend/tests/*.test.mjs` passou com 21 testes,
  zero falhas; `npm run build --prefix frontend` e
  `npm run lint --prefix frontend` passaram; `git diff --check` passou.
  Node emitiu aviso de API experimental para MockTimers.

## Falha de autenticação na integração

- Ferramenta: Codex. Orientação: alterações pontuais, preservar HMAC, transações,
  auditoria e idempotência; não expor dados sensíveis nem adicionar reenvio.
- Nenhum arquivo de produção do frontend alterado nesta tarefa.
- Contrato do backend para falha de autenticação entre serviços documentado:
  HTTP 502 com mensagem sanitizada em ProblemDetail.detail.

## Finalização do frontend

- Ferramenta: Codex. Solicitação: histórico por operação, revisão pontual de erros,
  polling, usabilidade e testes, preservando arquitetura, contratos e português.
  Sem reenvio manual, atualização de conta ou funcionalidades adicionais.
- Contratos inspecionados: EventoOperacaoRespostaDTO, TipoEventoOperacao,
  OperacaoController, GlobalExceptionHandler, criação e sincronização Java.
  Não houve alteração de backend ou Central.
- Implementação: ação Ver histórico pelo UUID local e Dialog Material UI com
  consulta cancelável, eventos mais recentes primeiro, descrição, data/hora pt-BR,
  nomes amigáveis e transições de integração/operação. Carregamento, vazio, erro
  e nova tentativa; fechamento por botão, Escape ou clique externo. Empates
  preservam ordem recebida. Metadados técnicos de integração não são exibidos.
- Erros: helper HTTP compartilhado para operações, agenda e autorizações;
  mensagens específicas para 400, 401/403, 409, 502, 503/504 e rede. Somente
  detalhes públicos conhecidos do backend são aceitos; demais corpos usam
  mensagens locais. Erros do parser JSON também usam mensagem local.
  Campos/seleção permanecem após falha de criação. Orientação para acompanhar
  pela listagem em caso de resultado incerto. Nenhum novo fluxo de reenvio.
- Usabilidade: rótulos de status compartilhados com formulário e histórico,
  rejeição/falha técnica com indicação de erro, confirmação de recebimento após
  criação sem anunciar conclusão da portabilidade. Colunas estáveis na tabela.
- Polling: solução existente preservada após revisão e testes; ciclos sequenciais
  de 10 segundos, deduplicação de IDs, somente página carregada e elegíveis,
  abort na troca de contexto/desmontagem e preservação de dados após falha.
- Testes: preservados os testes locais preexistentes de HTTP e polling. Adicionados
  testes de componentes reais com React Testing Library e JSDOM, dependências
  apenas de desenvolvimento, e loader TypeScript para node:test. Cobertura de
  abertura/fechamento, ordenação, eventos/transições/datas, carregamento, vazio,
  erros/reconsulta/cancelamento, status, manutenção dos campos após 502 e ciclo
  de polling da tela com falha e desmontagem. Script npm test adicionado.
- Limitações: fetch simulado, sem integração real nem inspeção visual em navegador.
  Histórico não atualiza sozinho enquanto aberto; reabrir consulta novamente.
  Revisão humana final ainda não registrada. Não houve decisão arquitetural nova.
- Validação final: `npm test --prefix frontend` passou com 39 testes, zero falhas;
  `npm run build --prefix frontend`, `npm run lint --prefix frontend` e
  `git diff --check` passaram. Node emite aviso experimental de MockTimers.
  Build mantém aviso de chunk acima de 500 kB (aproximadamente 1,08 MB minificado).
- Instalação das ferramentas de teste apontou sete alertas moderados no conjunto
  de dependências. `npm audit --omit=dev --json` retornou zero vulnerabilidades
  de produção. Atualização ampla das ferramentas fica fora desta entrega.
  A primeira tentativa de instalação não retornou no sandbox; cancelada e
  repetida com acesso ampliado, concluída com sucesso.
- Arquivos locais preexistentes em backend/target, análise técnica e testes
  foram preservados; o cliente HTTP foi ampliado e os testes de polling mantidos.
