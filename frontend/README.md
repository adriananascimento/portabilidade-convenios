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
- `/autorizacoes` apresenta o título e a descrição de Autorizações.
- `/operacoes` apresenta o título e a descrição de Operações.

O layout compartilhado está em `src/layouts/BaseLayout.tsx` e as páginas em
`src/pages/`. O React Router controla a navegação e a indicação da opção ativa.
No celular, o menu fica acima do conteúdo com as duas opções sempre acessíveis.
A interface utiliza CSS próprio, sem biblioteca visual ou fontes externas.

As páginas ainda não possuem formulários, integrações HTTP ou regras de negócio.
Em hospedagem estática, configure o servidor para servir `index.html` ao acessar
as rotas da aplicação diretamente (fallback de SPA).
