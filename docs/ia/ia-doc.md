# Registro de uso de IA

# Uso da IA — registro de autorização na Central

## Tarefa e orientações

Implementar `POST /autorizacoes` exclusivamente na aplicação `central-convenios`,
com respostas 201/200/400, persistência PostgreSQL, Flyway, validação e unicidade
sob concorrência. Seguir o AGENTS.md: domínio em português, camadas separadas,
testes organizados por feature, sem alterar o backend principal ou o frontend,
sem dependências novas ou funcionalidades adicionais.

## Resultado

Foram criados entidade e enum, Repository, Service, Controller, DTOs de entrada e
saída, migração V1 e testes HTTP de integração. O README foi complementado com
execução e testes. A restrição UNIQUE garante unicidade. A transação de inserção
usa TransactionTemplate com REQUIRES_NEW; conflitos são recuperados após rollback,
consultando a autorização ativa existente. A consulta prévia é uma otimização.
O timestamp é gerado em UTC com microssegundos para consistência com PostgreSQL.
Não foram implementadas revogação ou reativação, nem alteradas dependências.
As alterações prévias do usuário foram preservadas.

## Validações realizadas

- A tentativa inicial de `./mvnw test` no sandbox falhou ao acessar locks do cache Maven.
- Com acesso autorizado, a compilação revelou um erro de escape de string no teste,
  corrigido durante a implementação.
- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55434/central_convenios_testes ./mvnw test`:
  BUILD SUCCESS; 12 testes, nenhuma falha, nenhum erro, nenhum teste ignorado.
- PostgreSQL 17 em contêiner descartável, sem volume, separado do desenvolvimento;
  Flyway aplicou V1 e Hibernate validou o esquema. O teste concorrente provocou
  violações reais de UNIQUE (SQLSTATE 23505) e confirmou recuperação com HTTP 200.
- A configuração dos testes foi então ajustada para usar por padrão o banco exclusivo
  de testes e permitir substituição por `CENTRAL_TEST_DATABASE_URL`.
- Validação final com `./mvnw test` na pasta `central-convenios`: BUILD SUCCESS;
  12 testes, zero falhas, zero erros e zero ignorados, utilizando a configuração final.
- `git diff --check`: sem erros de whitespace.

# Uso da IA — consulta da agenda de parcelas

## Tarefa e orientações

Implementar `GET /beneficiarios/{beneficiarioId}/agenda` apenas na Central,
filtrando autorização ativa para o mesmo beneficiário e convênio e datas atuais
ou futuras. Preservar o registro de autorizações, backend principal, frontend e
dependências. Seguir a organização por camadas e testes por feature do AGENTS.md.
Criar V2, V3, DTOs, testes, documentação e informar validações reais.

## Resultado e decisões de implementação

Criados AgendaController, AgendaService, ParcelaRepository, Parcela,
StatusParcela e DTOs AgendaRespostaDTO/ParcelaRespostaDTO. A consulta JPQL usa
EXISTS para autorização ativa e filtra a data no banco, ordenando por data e
identificador como desempate. A transação de consulta é somente leitura.
O Clock é um bean UTC na aplicação, substituído por referência fixa em 2040 nos
testes. Não há mudança arquitetural de camadas nem novas dependências.

V2 cria parcelas com identificadores textuais, NUMERIC(15,2), DATE, status
DISPONIVEL e índice por beneficiário/data. Somente o estado solicitado foi
modelado; não foram inventadas transições nem filtro por status de parcela.
V3 insere seis parcelas fictícias, com dois beneficiários, dois convênios,
datas vencidas em 2020 e futuras em 2099. Não insere autorizações.
A migração de exemplos é aplicada em qualquer ambiente que execute V3.

Criados quatro testes HTTP de integração, com PostgreSQL via Testcontainers,
sem mocks. Cobrem os quatro cenários pedidos, inclusão de hoje, exclusão de
ontem, autorizações revogadas/ausentes e isolamento entre beneficiários e
convênios, além do contrato dos campos de resposta. README atualizado com
consulta, exemplos, referência UTC e execução real dos testes existentes.

## Validações realizadas

- A primeira execução de `./mvnw test` falhou antes da compilação por restrição
  de escrita nos locks do cache Maven no sandbox.
- Nova execução de `./mvnw test` em `central-convenios`, com acesso autorizado
  ao cache e Docker: BUILD SUCCESS, 17 testes, zero falhas, zero erros e zero
  ignorados (1 de contexto, 12 de autorização e 4 de agenda).
- Flyway aplicou V1/V2/V3 nos contêineres PostgreSQL 17 dos testes HTTP;
  Hibernate validou o esquema. O teste de contexto existente utilizou o banco
  local da porta 5434 e aplicou V2/V3 nele; seu uso da configuração normal da
  aplicação foi explicitado no README. Não foi alterado esse teste existente.
- Maven emitiu avisos de metadados de repositórios externos com HTTP 401 e
  de API obsoleta no teste; não impediram o build.
- `git diff --check`: sem erros de whitespace.


# Uso da IA — registro de operações de portabilidade

## Tarefa e orientações

Implementar somente POST /operacoes-portabilidade na central-convenios, com DTOs,
validação, idempotência UUID, reserva concorrente, migração incremental e testes.
Seguir as camadas e a nomenclatura do AGENTS.md, preservar autorização, agenda,
backend e frontend. Não implementar processamento ou consulta de operações.
O usuário solicitou este registro no caminho docs/ia/id-doc.md.

## Resultado e decisões

Criados Controller, Service, Repository, entidade OperacaoPortabilidade, enum
PENDENTE, DTOs e V4. Adicionado getter do beneficiário e consulta com lock ao
Repository de parcelas, sem alterar a consulta da agenda. A tabela de vínculos
é mapeada como ElementCollection de identificadores; possui PK composta,
FKs e UNIQUE por parcela. UUIDs e timestamp com fuso são tipos nativos no banco.
Não foi necessário duplicar valores monetários da tabela de parcelas.

Seguido o padrão existente de TransactionTemplate REQUIRES_NEW e recuperação
após rollback. UNIQUE na chave serializa inserções com a mesma chave; UNIQUE
na parcela impede dupla reserva. Locks pessimistas de parcelas em ordem de ID
reduzem risco de deadlock para conjuntos sobrepostos. Falhas desfazem operação
e vínculos. Comparação exata de beneficiário/conta e do conjunto de parcelas,
sem hash e sem considerar ordem. Repetições são recuperadas antes da validação
de disponibilidade. Disponibilidade usa status DISPONIVEL e data >= hoje UTC,
seguindo a elegibilidade já usada pela agenda. Não há novas dependências.

HTTP: 400 para entrada inválida, 422 para parcelas inexistentes/autorização
inativa e 409 para indisponibilidade ou conflitos. Criados nove testes HTTP de
integração com PostgreSQL real, incluindo três cenários de concorrência.


## Validação

A primeira tentativa de Maven no sandbox falhou antes da compilação por bloqueio
de escrita no cache ~/.m2. Execução posterior solicitada com acesso ao cache e Docker.

- Primeira execução completa de `./mvnw test`: BUILD SUCCESS, 26 testes,
  zero falhas, zero erros e zero ignorados.
- Após acrescentar a consulta pessimista ordenada de parcelas, nova execução de
  `./mvnw test` em central-convenios: BUILD SUCCESS, 26 testes, zero falhas,
  zero erros e zero ignorados. Inclui nove testes de operações, doze de
  autorização, quatro de agenda e um de contexto.
- Testes HTTP usaram PostgreSQL 17 via Testcontainers; Flyway aplicou V1–V4 e
  Hibernate validou o esquema. O teste de contexto existente aplicou V4 também
  no PostgreSQL local da porta 5434, usando a configuração normal da aplicação.
- Maven emitiu avisos de HTTP 401 em metadados de repositórios externos e de APIs
  obsoletas/configuração de testes existente, sem impedir o build.
- `git diff --check`: sem erros de whitespace.


# Uso da IA — consulta e processamento de operações (em andamento)

## Tarefa e orientações

O usuário solicitou GET por protocolo, processamento com @Scheduled, revalidação
determinística, reservas liberáveis após rejeição, atomicidade e concorrência,
testes sem sleeps, README e registro de IA. Foram lidos AGENTS.md, código,
migrações e testes existentes. Não alterar frontend nem backend principal.

## Resultado parcial e proposta pendente

Implementado GET somente leitura, DTO próprio para preservar o contrato do POST,
404, enum com três estados e V6 com data_processamento/motivo_rejeicao. A V5 já
amplia o CHECK de status, portanto foi preservada. Testes de consulta cobrem
pendente sem mutação, finalizadas com campos de resultado e protocolo ausente.

Enviada para aprovação humana a proposta de manter ElementCollection como
histórico e separar reserva ativa com unicidade no PostgreSQL; processamento
com lock pessimista e transação por operação. O AGENTS.md exige apresentação
para aprovação de decisões arquiteturais relevantes. Não foi implementada a
parte dependente dessa aprovação nem presumida revisão humana.

## Validação parcial

A primeira tentativa de Maven no sandbox falhou por escrita no cache ~/.m2.
Com acesso autorizado, `./mvnw -Dtest=OperacaoIntegracaoTest test` passou:
11 testes, zero falhas/erros/ignorados (antes da adição do teste de finalizadas).
A suíte completa `./mvnw test` passou: BUILD SUCCESS; 29 testes, zero falhas,
zero erros e zero ignorados. Inclui 12 testes de operações, 12 de autorizações,
4 de agenda e 1 de contexto. Integração HTTP com PostgreSQL descartável via
Testcontainers, Flyway V1–V6. O teste de contexto usou o datasource local
existente e aplicou V5/V6. `git diff --check` sem erros de whitespace.


# Uso da IA — processamento e reservas após aprovação

## Autorização e orientações

O usuário aprovou explicitamente separar histórico e reservas ativas, exigindo
preservação de histórico, transações para criação/liberação, retenção em operações
confirmadas, proteção concorrente e lógica testável sem agendamento. Mantidos
os limites da solicitação inicial, sem frontend, backend principal ou dependências
novas. O AGENTS.md e a proposta ADR 0002 orientaram a implementação.

## Resultado

V7 migra reservas de vínculos pendentes/confirmados, remove somente a unicidade
global do histórico e cria PK por parcela e FK composta para vínculo histórico.
ElementCollection preservado. POST insere reservas após flush do histórico na
mesma transação. Não usa merge para inserir, evitando sobrescrita de uma reserva.
Agenda passa a excluir reservas ativas e exigir status base DISPONIVEL.

ProcessamentoOperacaoService seleciona até 100 pendentes, aplica atraso configurável,
bloqueia operação/parcelas e revalida beneficiário, data UTC, status e autorização
ativa (com lock compartilhado). Finaliza com data e motivo determinístico. Libera
reservas somente na rejeição, na mesma transação. REQUIRES_NEW por operação;
estados finais são ignorados. Erros técnicos geram rollback e log do protocolo,
permitindo nova tentativa sem interromper as demais operações do lote.
AgendamentoOperacaoService usa @Scheduled com intervalo configurável e pode ser
desabilitado; testes invocam a lógica diretamente com Clock fixo e sem sleeps.
README e ADR atualizados. Não foi presumida revisão humana do código final.

## Validação em andamento

Primeira tentativa no sandbox falhou por escrita nos locks ~/.m2, antes de
compilar. Com acesso autorizado, a suíte OperacaoIntegracaoTest passou:
17 testes, zero falhas, erros ou ignorados. Após essa execução, o teste de falha
foi reforçado para disparar no commit e foi acrescentada a migração de vínculos
prévios em esquema isolado. Resultado final da suíte completa registrado a seguir.


## Validação final e revisão

`./mvnw test` em central-convenios: BUILD SUCCESS, 35 testes, zero falhas,
zero erros e zero ignorados. Foram 17 de operações, 1 de migração de reservas,
12 de autorizações, 4 de agenda e 1 de contexto. Flyway aplicou V7 e Hibernate
validou o esquema; integração com PostgreSQL 17 real via Testcontainers.
O teste de contexto existente utilizou o datasource local e aplicou V7 nele,
com agendador explicitamente desabilitado. O teste de migração criou esquema
isolado até V6, inseriu vínculos prévios, aplicou V7 e verificou backfill,
histórico e violações reais de unicidade/FK. O teste de rollback utilizou
constraint trigger adiado até o commit, removido no finally. A concorrência
entre dois processadores retornou uma execução efetiva e outra ignorada.
`git diff --check`: sem erros de whitespace.

# Uso da IA — retry no processamento assíncrono

## Tarefa e orientações

Analisar central-convenios e apresentar plano antes das alterações. Implementar
até cinco tentativas, backoff de 5/10/20/40 segundos, classificação técnica,
rollback e registro independente, estado FALHA_TECNICA com reservas preservadas,
proteção concorrente, testes PostgreSQL/Testcontainers e documentação. Preservar
@Scheduled, locks, revalidações, Clock, camadas e migrations existentes. Não
alterar backend/frontend, dependências nem o arquivo preexistente não rastreado
docs/analise-tecnica-implementacao.md.

## Resultado

Plano apresentado após leitura do serviço, entidade, repositories, migrations,
testes e documentação. V9 adicionada após confirmar V8 como última migration.
Entidade e enum incluem metadados de retry e estado técnico terminal. Seleção
de lote e revalidação verificam próximo horário. Falhas são registradas após
rollback em REQUIRES_NEW, com lock, status e contador revalidados. Erros têm
mensagens fixas sem dados originais; não há retry genérico de RuntimeException.
Tentativas incluem conclusões de sucesso/rejeição; ultimo_erro preserva a última
falha. Criada ADR-0003 com classificação e limites da janela rollback/registro.
README atualizado. Nenhuma revisão humana foi presumida.

Testes ampliados na feature operacao: falhas reais de commit, progressão temporal,
limite, recuperação, erro permanente e não classificado, concorrência de falhas,
registro tardio após sucesso e reservas/histórico. Uso limitado de doubles que
delegam aos repositories reais para controlar a janela de concorrência e falhas
de programação; o banco, os locks e as transações continuam reais.

## Validações em andamento

A tentativa inicial de ./mvnw test foi bloqueada pela escrita no cache Maven.
Com acesso autorizado a Maven/Docker, a execução inicial de OperacaoIntegracaoTest
teve 21 testes, zero falhas e um erro na instrumentação Mockito de um proxy Spring
no cenário de registro tardio. Substituído o spy do proxy por mock de interface
com delegação ao repository real. Acrescentados cenários de falhas concorrentes
e RuntimeException não classificada. Suíte completa executada em seguida;
resultado final registrado abaixo.

## Validação final

- ./mvnw test em central-convenios, com acesso autorizado ao cache e Docker:
  BUILD SUCCESS, 41 testes, zero falhas, zero erros e zero ignorados.
- OperacaoIntegracaoTest: 23 testes, incluindo os seis novos cenários e o teste
  existente de rollback ajustado para SQLSTATE transitório e espera por Clock.
- Integrações usam PostgreSQL 17 via Testcontainers. Flyway validou/aplicou V1–V9
  e Hibernate validou o esquema. O teste de migração existente também passou,
  aplicando as migrations novas sobre vínculos anteriores à V7 em esquema isolado.
- O teste de contexto existente utilizou o PostgreSQL local localhost:5434 e
  aplicou V9 nele, com agendamento desabilitado. Não foram alteradas suas configurações.
- Maven emitiu avisos HTTP 401 de metadados de repositórios externos; o build passou.
- git diff --check: sem erros de whitespace.

Limites: queda/indisponibilidade durante registro independente pode perder a
contabilização; não há reconciliação automática nem diário de cada tentativa.
Detalhes em ADR-0003.
