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

## Criação local e integração idempotente — 14/11/2026

- Ferramenta: Codex. Tarefa: adaptar somente a criação de operações para usar a
  persistência existente, integrar com a Central e preservar idempotência.
- Aprovação humana: o fluxo foi apresentado antes das alterações. O usuário
  aprovou e exigiu HTTP 202 também nas repetições, confirmação do significado de
  `FALHA_TECNICA`, documentação das operações paradas e tratamento da disputa de
  inserção com rollback seguido de consulta em nova transação.
- Confirmação no código da Central: `OperacaoPortabilidade.registrarFalha` atribui
  `FALHA_TECNICA` ao encerrar o processamento; o DTO oficial e o POST idempotente
  expõem o estado persistido. O enum local e seu CHECK foram ampliados; falhas
  de integração continuam exclusivamente em `StatusIntegracao`.
- Implementação: validação do payload, registro confirmado antes do HTTP,
  exclusividade de envio para quem inseriu, comparação do conteúdo e HTTP 409,
  persistência da resposta e reprodução com 202. Migration V2 guarda a data de
  criação da Central. Transações curtas e independentes; nenhuma durante o HTTP.
- Falhas: 4xx definitivos e conexão não estabelecida usam `FALHA_ENVIO`;
  timeout, 408, 5xx e respostas inválidas usam `ENVIO_INDETERMINADO`.
  Registros existentes sem confirmação retornam 503 sem reenvio.
- Escopo: nenhuma mudança na Central ou frontend, nenhuma dependência nova,
  auditoria, polling ou retry automático. README e ADR-0006 documentam que
  operações pendentes/indeterminadas podem ficar paradas após falhas ou
  reinicialização; retry com backoff fica para etapa posterior.
- Testes adicionados: `OperacaoCriacaoTest`, com PostgreSQL e schema temporário,
  e `OperacaoClientTest`, com servidor HTTP local. Cobrem sucesso, status oficiais,
  resposta idempotente 202, ordem das parcelas, validação, conflito, concorrência
  com conteúdo igual/divergente, falhas conhecidas/incertas e ausência de transação
  durante a chamada HTTP. A concorrência sincroniza consultas reais antes das
  inserções para forçar a disputa pela constraint única.
- Execuções intermediárias identificaram colisões de imports e problemas na
  instrumentação do teste concorrente (chamada abstrata via spy e stream JPA
  consumido fora da transação). Esses problemas dos testes foram corrigidos,
  mantendo consultas reais e constraints do PostgreSQL.
- Revisão humana: aprovação prévia do fluxo e ajustes acima; revisão final da
  implementação ainda não registrada. Alterações preexistentes foram preservadas.
- Validação final: `cd backend && ./mvnw -o -q test` — exit code 0;
  38 testes, zero falhas, erros ou ignorados (16 casos novos). `git diff --check`
  passou. Flyway V2 e validação Hibernate executados em PostgreSQL real. Maven
  precisou de permissão ampliada para acessar o cache `.m2` e o banco local.
  O teste de contexto preexistente aplica migrations no schema padrão do banco
  configurado; os novos testes de criação usam schema temporário removido ao final.
  Arquivos gerados de status do compilador em `backend/target` foram atualizados.


## Listagem local de operações — 14/11/2026

- Ferramenta: Codex. Escopo: somente a listagem do backend, testes e documentação.
- Aprovação humana: DTO e consulta local aprovados antes da implementação. O
  usuário excluiu explicitamente os aliases `protocolo` e `status` e adiou a
  adaptação do frontend. Revisão humana final ainda não registrada.
- Implementação: `OperacaoListagemDTO`, conteúdo de `OperacaoPaginaDTO` adaptado,
  repository existente com `PageRequest` e `dataCriacao DESC, id DESC`, leitura
  transacional e remoção do método remoto de listagem que ficou sem uso.
  Nenhuma alteração no fluxo de criação, na Central ou no frontend.
- Contrato incompatível e próximos ajustes do frontend documentados no README
  e na ADR-0007. Estados e data representam os valores locais.
- Testes novos: `OperacaoListagemTest`, PostgreSQL real, schema temporário e
  rollback por caso; verifica paginação, desempate por ID, vazio, página fora do
  total, seis campos JSON sem aliases, protocolo/status nulos, estados preenchidos,
  data local e ausência de interações com o client da Central.
- A primeira tentativa de `./mvnw -o -q test` foi bloqueada pelos locks do cache
  Maven no sandbox; a validação foi repetida com permissão ampliada.
- Validação final: `cd backend && ./mvnw -o -q test` terminou com código 0:
  41 testes, zero falhas, erros ou ignorados, incluindo os três novos casos e
  os testes existentes de criação, client, persistência e autorizações. Flyway e
  Hibernate executaram no PostgreSQL local. Arquivos de status do compilador em
  `backend/target` foram atualizados pelo Maven. `git diff --check` passou.

## Auditoria de operações — 14/11/2026

- Ferramenta: Codex. Escopo autorizado: auditoria persistente e endpoint de
  histórico do backend, migration, testes e documentação. Central e frontend
  não foram alterados; sem polling, sincronização ou retry automático.
- Aprovação humana: modelo, tipos, transações e consulta apresentados antes de
  editar arquivos e aprovados. Orientações adicionais: tentativa registrada após
  HTTP, distinguir falha local de comunicação, atomicidade, idempotência e
  trilha append-only. Revisão humana final ainda não registrada.
- Implementação: EventoOperacao imutável no Hibernate, repository restrito,
  DTO de resposta, enum, migration V3 com FK/constraints/índice; consulta local
  cronológica com desempate por UUID e 404 via ProblemDetail.
- Estado e eventos compartilham transações. Falha local após sucesso da Central
  tenta gravar ENVIO_INDETERMINADO em nova transação, com descrição específica;
  falhas dessa recuperação não substituem o último estado confirmado.
- Testes de criação ampliados para verificar eventos antes/depois da chamada,
  sucesso, falhas, idempotência inclusive concorrente, HTTP de histórico, 404,
  histórico vazio e desempate. Testes de rollback usam persistência e flush
  reais antes da exceção simulada, incluindo falha da recuperação. Teste de
  listagem adaptado apenas à nova dependência do construtor do service.
- Limites registrados no README e ADR-0008: interrupções antes do commit podem
  deixar histórico incompleto; não há atomicidade distribuída ou backfill;
  imutabilidade no fluxo normal não impede SQL administrativo.
- A primeira execução no sandbox falhou antes dos testes por acesso aos locks
  do cache Maven. Execuções com permissão ampliada acessaram cache e PostgreSQL.
- Validação final: `cd backend && ./mvnw -o -q test` terminou com código 0:
  47 testes, zero falhas, erros ou ignorados. Migration V3 e validação Hibernate
  executadas no PostgreSQL local; cenários novos usam schema temporário removido
  ao final. `git diff --check` passou. Os quatro arquivos rastreados de status do
  compilador em `backend/target` já tinham alterações e foram atualizados pelo
  Maven; demais alterações preexistentes foram preservadas.

## Sincronização manual de status — 14/11/2026

- Ferramenta: Codex. Escopo: somente backend, endpoint manual, integração de
  consulta, controle otimista, migration, testes e documentação. Sem alteração
  na Central/frontend, polling, retry automático ou agendamento.
- Aprovação humana antes da implementação: POST por UUID local, retorno com
  OperacaoListagemDTO, timeouts do RestClient e @Version. Cuidados adicionais:
  validar transições reais, preservar estados finais, FALHA_TECNICA consultável,
  atomicidade e não sobrescrever versões mais recentes. Revisão humana final
  ainda não registrada.
- Análise da Central (somente leitura): finalizar/registrarFalha e seleção do
  processamento permitem saída apenas de PENDENTE, para CONFIRMADA, REJEITADA
  ou FALHA_TECNICA. Não há saída de FALHA_TECNICA implementada. O backend permite
  consultá-la, mas rejeita resposta diferente como inconsistente (502).
- Implementação: serviço/controller de sincronização, DTO remoto, tratamento
  específico de erros em ProblemDetail, consulta no cliente existente, @Version
  na operação e migration V4 com ampliação do enum de eventos. Reutilização do
  DTO local de listagem; nenhum contrato existente foi alterado.
- Transações curtas antes/depois do HTTP, versão inicial revalidada, flush
  versionado e evento atômico. Conflito faz nova leitura local, sem nova chamada
  HTTP: retorna estado final/igual ou 409. Estado igual não altera versão/data.
- RestClientConfig agora conecta o requestFactory configurado ao builder.
  Testes verificam configuração de conexão (3s) e leitura (5s), timeout de leitura
  com servidor HTTP local real, conexão recusada, erros HTTP e JSON/status inválido.
- Testes com PostgreSQL e schema temporário cobrem transições, histórico único,
  operações sem protocolo/inexistentes/finalizadas, FALHA_TECNICA, respostas
  inválidas, falhas, rollback após flush real e concorrência com respostas finais
  iguais/divergentes. Barreira na inserção dos eventos força a disputa de versão.
- Tentativas no sandbox falharam antes dos testes por acesso aos locks do cache
  Maven. Validação repetida com permissão ampliada para cache e PostgreSQL local.
- Validação final: `cd backend && ./mvnw -o -q test` terminou com código 0:
  81 testes, zero falhas, erros ou ignorados (34 casos novos). Migration V4 e
  validação Hibernate executadas em PostgreSQL real; schemas temporários dos
  testes novos removidos ao final. `git diff --check` passou. Maven atualizou
  quatro arquivos rastreados de status do compilador em backend/target, que já
  tinham alterações. Demais alterações preexistentes foram preservadas.
- Limites: sem versão temporal remota ou garantia de atualização contínua;
  solicitações concorrentes podem consultar HTTP em paralelo. Timeout real de
  conexão não foi simulado; a configuração de 3s foi verificada no factory efetivo.
