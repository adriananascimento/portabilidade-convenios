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

## Revisão humana pendente

Pendente revisão humana do código, contrato HTTP, limites de validação, tratamento
transacional e testes. Nenhuma aprovação ou revisão humana foi presumida.
O comportamento de registro para pares previamente REVOGADA permanece fora do escopo.

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
