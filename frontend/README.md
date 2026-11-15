# Frontend — Portabilidade de Convênios

Interface em React, TypeScript e Vite para a Plataforma de Portabilidade de
Convênios de Desconto em Folha.

## Execução local

Com Node.js 22.21.1 ou superior compatível com o Vite instalado, execute neste diretório:

```sh
npm ci
npm run dev
```

Acesse o endereço informado pelo Vite no terminal.

## Validação

```sh
npm test
npm run build
npm run lint
```

O build executa a verificação de TypeScript (`tsc -b`) antes de gerar `dist/`.
Para servir o resultado localmente, utilize `npm run preview`.

## Layout e navegação

- `/` redireciona para `/autorizacoes`, substituindo a entrada no histórico.
- `/autorizacoes` lista autorizações e permite cadastrar pelo formulário em modal.
- `/operacoes` lista operações, permite solicitar portabilidade e consultar o histórico.

O layout compartilhado está em `src/layouts/BaseLayout.tsx` e as páginas em
`src/pages/`. O React Router controla a navegação e a indicação da opção ativa.
No celular, o menu fica acima do conteúdo com as duas opções sempre acessíveis.
O menu lateral e o layout compartilhado mantêm o CSS existente. A tela de
Autorizações utiliza Material UI com Emotion e MUI X Data Grid Community,
com tema nas cores e fontes da interface, sem fontes externas.

A listagem utiliza paginação server-side de 10 registros em
`GET /api/autorizacoes?pagina=0&tamanho=10`, com páginas iniciando em zero.
A contagem do DataGrid vem de `totalElementos`; ordenação e filtros locais
ficam desabilitados para preservar a ordem retornada pelo backend.
O cadastro envia `beneficiarioId` e `convenioId` para `POST /api/autorizacoes`.
Após salvar, o modal fecha e a primeira página é consultada novamente.
Erros permitem nova tentativa, e o formulário impede fechamento durante o envio.
O Vite encaminha `/api` ao backend conforme `vite.config.ts`.
Em hospedagem estática, configure o servidor para servir `index.html` ao acessar
as rotas da aplicação diretamente (fallback de SPA).

## Acompanhamento automático de operações

A página `/operacoes` sincroniza somente registros da página atual com protocolo
na Central e `statusOperacao` igual a `PENDENTE` ou nulo. `CONFIRMADA`, `REJEITADA`
e `FALHA_TECNICA` encerram o acompanhamento automático. Isso não altera as regras
do endpoint do backend, que ainda permite consultas de `FALHA_TECNICA`.

Após carregar a listagem, o primeiro ciclo aguarda 10 segundos. Os POSTs para
`/api/operacoes/{id}/sincronizar-status` usam o ID local, sem corpo, e são
sequenciais. Um novo ciclo começa 10 segundos após a conclusão do anterior,
usando `setTimeout` recursivo. O intervalo padrão está em
`src/polling/pollingOperacoes.ts`; não há agendamento quando não há elegíveis.

Cada resposta atualiza a linha pelo ID, preservando ordem, paginação, totais e
formulário. Falhas mantêm os dados conhecidos, exibem aviso e permitem nova
tentativa no próximo ciclo. Cancelamentos não exibem erro. Trocar de página,
recarregar a listagem ou sair da tela cancela o contexto anterior e descarta suas
respostas tardias. A proteção contra duplicidade vale por instância da tela;
abas distintas podem consultar a mesma operação. Cancelar o fetch não garante
interromper processamento já iniciado no servidor.

Os testes usam `node:test`, fetch e temporizadores simulados. Os testes de
interface montam os componentes reais com React Testing Library e JSDOM,
instalados somente como dependências de desenvolvimento:

```sh
node --test frontend/tests/*.test.mjs
```

Execute esse comando na raiz do repositório. Os testes não substituem validação
visual ou integração real com o backend.


## Histórico e mensagens

A ação **Ver histórico** em cada linha consulta `GET /api/operacoes/{id}/eventos`
pelo ID local. O Dialog reutiliza Material UI e oferece fechamento pelo botão,
Escape e clique externo, contenção/restauração de foco e rolagem em telas menores.
Apresenta eventos em ordem decrescente de data (empates preservam ordem da API),
nomes amigáveis, descrição e status anterior/novo quando presentes. Datas usam
pt-BR no fuso do navegador. Carregamento, vazio e erro têm mensagens próprias;
a nova tentativa é apenas de consulta. A requisição é cancelada ao fechar.

Os erros HTTP 400, 401/403, 409, 502 e 503/504 têm mensagens específicas, além
de falhas de rede. Somente mensagens públicas conhecidas do backend são
reaproveitadas; detalhes desconhecidos e corpos técnicos são substituídos.
Novas mensagens públicas precisam ser revisadas em `src/api/errosApi.ts`.
Os tipos de eventos e campos seguem `EventoOperacaoRespostaDTO` e
`TipoEventoOperacao` do backend.

Falhas de envio preservam os campos e a seleção. O retorno de criação exibe
protocolo e status amigável; a confirmação atualiza a primeira página e mostra
um aviso de recebimento para processamento. Isso não comprova conclusão da
portabilidade. Não foi adicionado reenvio manual.

Limitações: histórico é atualizado ao abrir ou tentar novamente; não possui
polling próprio nem paginação porque o endpoint retorna lista. Não houve
inspeção visual em navegador nem integração com serviços reais nesta etapa.
Melhorias futuras: validação ponta a ponta e avaliação da divisão do bundle
(o build atual avisa sobre chunk acima de 500 kB).
