# Uso de IA — Backend

## Ferramentas utilizadas

Não há ferramenta ou modelo de IA identificado para implementações do backend.
O histórico Git foi consultado nesta reorganização documental.

## Finalidade

Registrar a evidência disponível sobre o backend principal, distinguindo suas
atividades das implementações auxiliadas por IA na Central de Convênios.

## Implementações auxiliadas

Não há implementação do backend comprovadamente auxiliada ou gerada por IA nos
registros existentes. O histórico identifica as seguintes atividades, sem
comprovar o modo de desenvolvimento:

| Commit | Atividade |
| --- | --- |
| `7f3f4c0` | Estrutura inicial Spring Boot, Maven e configuração de banco/Compose. |
| `9e50dcb` | RestClient para a Central, configuração e DTOs de agenda. |
| `9446e8f` | Controller e Service de consulta em `GET /api/beneficiarios/{beneficiarioId}/agenda`. |
| `2f84f96` | Tratamento de indisponibilidade da Central com exceção própria e resposta HTTP 503. |

## Decisões humanas

Não há registro específico de aprovação ou revisão humana dessas implementações.
A autoria dos commits não permite afirmar desenvolvimento exclusivamente humano.
A [ADR-0001](../adr/0001-isolamento-da-central-de-convenios.md) documenta o
isolamento entre backend e Central e foi preservada, sem inferir sua autoria.

## Validações

Não foram encontrados resultados documentados de validações dessas atividades
no registro de IA. Os testes da Central não comprovam validação do backend.
Nenhum teste da aplicação foi executado nesta reorganização documental.

## Limitações

A reorganização atual foi realizada com auxílio do Codex: leitura do registro
anterior e do histórico Git, separação dos três documentos e atualização dos links
no README e da referência no AGENTS.md. Trata-se de documentação transversal,
sem implementação de backend, Central ou frontend. O usuário definiu o escopo;
não há revisão humana final documentada até esta edição. As verificações desta
edição são documentais e não substituem testes das aplicações.

## Listagem paginada de autorizações — 13/11/2026

- Tarefa: GET /api/autorizacoes integrado à Central pelo client existente.
- Trabalho do Codex: endpoint, delegação no service, chamada RestClient e
  AutorizacoesPaginaDTO; parâmetros e metadados preservados; sem repasse dos
  headers remotos. O contrato do POST foi preservado.
- Tratamento: falhas HTTP, conexão, leitura e corpo ausente na listagem usam
  CentralIndisponivelException e o handler 503 existente, sem expor corpo remoto.
- Testes adicionados: AutorizacaoIntegracaoTest usa MockMvc no controller e
  RestClient real contra servidor HTTP local; cobre parâmetros, metadados,
  ausência de header interno, falha HTTP, conexão recusada e validação local.
- Validação: ./mvnw test — 5 testes, zero falhas/erros/ignorados, BUILD SUCCESS.
- Orientações: escopo limitado às autorizações, português, camadas existentes,
  sem bibliotecas novas e sem alterar operações de portabilidade. Alterações locais
  preexistentes foram preservadas. README atualizado com uso e contrato JSON.
- Execução: primeira tentativa Maven foi bloqueada pelo sandbox no cache .m2;
  repetição com permissão de execução ampliada concluiu os testes. Maven emitiu
  avisos HTTP 401 para metadados de repositórios externos, sem impedir o build.

## Persistência local de operações — 14/11/2026

- Tarefa solicitada: analisar as configurações e implementar somente a estrutura
  local de persistência, sem modificar Central, frontend ou fluxos existentes.
- Aprovação humana: o usuário aprovou parcelas como coleção de identificadores,
  conta embutida, UUIDs, datas e constraints. Solicitou separar `StatusIntegracao`
  de `StatusOperacao`, mantendo o segundo inicialmente nulo.
- Trabalho do Codex: entidade `OperacaoPortabilidade`, embutível `ContaDestino`,
  dois enums, repository JPA, migration V1, testes de persistência, README e
  ADR-0005. Não foram adicionadas dependências nem removidas configurações.
- Testes: `cd backend && ./mvnw test` — BUILD SUCCESS; 22 testes, zero falhas,
  erros ou ignorados. Inclui 17 casos novos em `OperacaoPersistenciaTest` e os
  cinco testes preexistentes do backend.
- Cobertura nova: leitura por chave idempotente, UUID local, conta e parcelas,
  status/protocolo inicialmente nulos, atualização de status e datas preservando
  criação, chave duplicada, campos obrigatórios, CHECKs dos enums, parcelas
  duplicadas, FK local e identificador de parcela nulo. A mesma parcela é aceita
  em operações diferentes. Flyway e validação Hibernate executados em PostgreSQL.
- Isolamento: os casos novos usam schema aleatório temporário, removido ao final,
  e rollback por teste. O teste de contexto existente continua usando a
  configuração padrão e aplica migrations pendentes no banco local do backend.
- Execução: a primeira tentativa Maven foi impedida pelo sandbox ao acessar os
  locks do cache .m2; a execução com permissão ampliada concluiu com sucesso.
  `git diff --check` também passou.
- Revisão humana: a modelagem foi aprovada antes da implementação. Alterações preexistentes em
  outros arquivos foram preservadas. Os arquivos de status do compilador em
  `backend/target` são gerados pelo Maven durante a validação.
