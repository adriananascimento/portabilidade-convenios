# Frontend — Portabilidade de Convênios

Interface em React, TypeScript e Vite para a Plataforma de Portabilidade de
Convênios de Desconto em Folha.

## Execução local

Com Node.js compatível com o Vite instalado, execute neste diretório:

```sh
npm ci
npm run dev
```

Acesse o endereço informado pelo Vite no terminal.

## Validação

```sh
npm run build
npm run lint
```

O build executa a verificação de TypeScript (`tsc -b`) antes de gerar `dist/`.
Para servir o resultado localmente, utilize `npm run preview`.

## Layout e navegação

- `/` redireciona para `/autorizacoes`, substituindo a entrada no histórico.
- `/autorizacoes` lista autorizações e permite cadastrar pelo formulário em modal.
- `/operacoes` apresenta o título e a descrição de Operações.

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

Os testes HTTP e do controlador de polling usam `node:test`, fetch simulado e
temporizadores simulados, sem dependências adicionais:

```sh
node --test frontend/tests/*.test.mjs
```

Execute esse comando na raiz do repositório. Os testes não substituem validação
visual ou integração real com o backend.
