# Portabilidade de Convênios

## Central de Convênios: registro de autorização

A aplicação `central-convenios` é independente do backend e atende na porta 8081.
Requisitos: Java 21, Docker com Compose e acesso às dependências Maven.

```sh
cd central-convenios
docker compose up -d --wait
./mvnw spring-boot:run
```

O Flyway aplica as migrações V1 (autorizações), V2 (parcelas), V3 (dados fictícios),
V4 (operações), V5 (estados finais), V6 (resultado do processamento) e V7
(reservas ativas) automaticamente. O Hibernate apenas valida
o esquema. O PostgreSQL de desenvolvimento usa a porta 5434; as credenciais locais
estão no Compose e em `application.yaml`. Em outros ambientes, configure
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`.

```sh
curl -i -X POST http://localhost:8081/autorizacoes \
  -H 'Content-Type: application/json' \
  -d '{"beneficiarioId":"BEN-001","convenioId":"CONV-001"}'
```

O primeiro registro retorna `201`; reenvios do mesmo par retornam `200` com a
mesma autorização. A resposta contém `autorizacaoId` (UUID), `beneficiarioId`,
`convenioId`, `dataAutorizacao` (gerada pelo servidor em UTC) e `status: ATIVA`.
Campos ausentes, nulos, vazios, compostos apenas por espaços ou com mais de 100
caracteres retornam `400`; corpo ausente ou JSON malformado também retorna `400`.
Os identificadores são preservados, sem normalização de espaços ou caixa.

A restrição única no PostgreSQL garante a unicidade, inclusive sob concorrência.
A consulta prévia evita inserções desnecessárias em reenvios; se duas inserções
competirem, a transação perdedora sofre rollback antes da consulta que recupera
a autorização vencedora. A data usa precisão de microssegundos para manter a
resposta inicial consistente com a persistência no PostgreSQL.
Não há revogação nem reativação nesta etapa; o comportamento para um registro
`REVOGADA` previamente inserido por outro meio não faz parte deste contrato.

### Testes da Central

Os testes de integração de autorização e agenda exercitam HTTP, JPA e Flyway com
PostgreSQL real criado pelo Testcontainers. Requerem Docker ativo e acesso à imagem
`postgres:17-alpine`; os contêineres são descartáveis e não usam o banco de desenvolvimento.
O teste existente `CentralConveniosApplicationTests` usa a configuração normal da
aplicação, portanto requer também o PostgreSQL local (ou variáveis de datasource
apontando para um banco exclusivo de testes).

```sh
# Na pasta central-convenios, com Docker ativo:
docker compose up -d --wait
./mvnw test
# Somente as suítes HTTP, com bancos isolados pelo Testcontainers:
./mvnw -Dtest=AutorizacaoIntegracaoTest,AgendaIntegracaoTest test
```

A suíte de autorização cobre criação, reenvio, validação e oito requisições
concorrentes. A suíte de agenda cobre autorização ativa, ausente e revogada,
isolamento entre beneficiários e convênios, inclusão de hoje, exclusão de vencidas,
ordenação entre convênios e listas vazias. A referência temporal é fixa nos testes,
sem depender do dia da execução.

## Consulta da agenda de parcelas

```sh
curl http://localhost:8081/beneficiarios/BEN-001/agenda
```

Retorna `200 OK` com `beneficiarioId` e `parcelas`. Cada parcela contém `parcelaId`,
`convenioId`, `valor`, `dataDesconto` e `status`. Apenas parcelas do beneficiário
com autorização `ATIVA` para o mesmo convênio, status `DISPONIVEL`, data de desconto
maior ou igual a hoje e sem reserva ativa são retornadas, em ordem crescente de data (desempate por identificador).
A data de referência usa UTC. Sem parcelas elegíveis, inclusive para beneficiário
sem registros, retorna `parcelas: []`.

A V2 cria `parcelas` com identificadores textuais, valor `NUMERIC(15,2)`, data
`DATE` e status `DISPONIVEL`. A disponibilidade efetiva também depende da ausência
de reserva ativa: operações pendentes ou confirmadas tornam a parcela indisponível. O índice por beneficiário e data auxilia
a seleção. Não há endpoint de cadastro nem vínculo obrigatório com autorização:
a parcela pode existir antes do consentimento e só fica visível quando autorizado.

A V3 inclui parcelas fictícias de `BEN-001` e `BEN-002`, dos convênios `CONV-001`
e `CONV-002`, com datas vencidas em 2020 e futuras em 2099. Para consultar dados
visíveis de `BEN-001`, registre sua autorização para `CONV-001` com o POST mostrado
acima; a agenda então inclui `PAR-002` e `PAR-001`, nessa ordem. A migração não
insere autorizações. Os dados fictícios serão inseridos em todo banco que aplicar
V3; as datas futuras dos exemplos permanecem válidas até 2099.

## Registro de operações de portabilidade

Após registrar as autorizações dos convênios envolvidos:

```sh
curl -i -X POST http://localhost:8081/operacoes-portabilidade \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 89acac52-923a-4d4b-ab6a-776d7ad993be' \
  -d '{"beneficiarioId":"BEN-001","parcelasIds":["PAR-001","PAR-002"],"contaDestino":{"banco":"001","agencia":"1234","conta":"123456-7"}}'
```

Retorna `202 Accepted` com `{"protocolo":"UUID","status":"PENDENTE"}`.
O header é obrigatório e deve ter a representação UUID completa com hífens.
Todos os campos são obrigatórios, os textos aceitam até 100 caracteres e a lista
não pode ser vazia nem conter identificadores repetidos. Valores textuais são
preservados, sem normalização. A ordem da lista não altera o conteúdo para
idempotência. A mesma chave e o mesmo conteúdo retornam sempre o protocolo
original, mesmo após a reserva; conteúdo diferente com a mesma chave retorna `409`.

Erros: `400` para header/corpo inválido ou parcelas repetidas; `422` para parcela
inexistente ou autorização ausente/revogada em qualquer convênio; `409` para
parcela de outro beneficiário, vencida, com status diferente de `DISPONIVEL`,
já reservada ou chave reutilizada com conteúdo diferente. A data de desconto
precisa ser igual ou posterior a hoje em UTC, seguindo a elegibilidade da agenda.
Falhas desfazem toda a operação, sem reservar parcialmente as parcelas.

A V4 cria `operacoes_portabilidade` e `operacoes_parcelas`. Protocolo e chave
usam UUID, criação usa timestamp com fuso; valores das parcelas continuam em
`NUMERIC(15,2)` na tabela existente. O vínculo tem chave primária composta,
estrangeiras para operação/parcela. A unicidade global original da parcela é
substituída pela V7: o histórico aceita novas tentativas e a tabela
`reservas_parcelas` mantém a unicidade das reservas ativas. A chave de idempotência
também é única. O Service usa transação independente, disputa a chave primeiro e
bloqueia parcelas em ordem de identificador para evitar reservas concorrentes e
ciclos de locks. As restrições do banco também protegem contra duplicidade;
a recuperação da chave vencedora ocorre depois do rollback da transação perdedora.
O vínculo é uma coleção JPA de identificadores persistida na tabela relacional,
sem expor entidades na API. Não há novas dependências.

O POST registra operações `PENDENTE` e cria todas as reservas na mesma transação.
Nenhum processamento ocorre na requisição. A consulta não modifica o estado.

```sh
cd central-convenios
./mvnw -Dtest=OperacaoIntegracaoTest test
```

A suíte de operações usa HTTP e PostgreSQL via Testcontainers e cobre criação,
validações, rollback, autorizações por convênio, idempotência e concorrência com
chaves iguais e diferentes. Para todas as suítes, execute `./mvnw test`, mantendo
o banco local disponível para o teste de contexto existente.

## Consulta de operação de portabilidade

```sh
curl http://localhost:8081/operacoes-portabilidade/550e8400-e29b-41d4-a716-446655440000
```

Retorna 200 com `protocolo`, `status` (`PENDENTE`, `CONFIRMADA` ou `REJEITADA`),
`dataCriacao`, `dataProcessamento` e `motivoRejeicao`. As datas usam ISO-8601 com
fuso; a aplicação gera datas em UTC. Os dois últimos campos são nulos enquanto
pendente. Protocolo inexistente retorna 404. A consulta é somente leitura.
A resposta do POST continua contendo somente protocolo e status.
A V5 existente amplia os estados; V6 adiciona os campos do processamento sem
alterar migrações anteriores.

## Processamento assíncrono e reservas

O `@Scheduled` chama o serviço de processamento em segundo plano. Configuração
em `central-convenios/src/main/resources/application.yaml`:

```yaml
central:
  processamento:
    habilitado: true
    intervalo: PT5S
    atraso-minimo: PT10S
```

As durações usam ISO-8601. `intervalo` é o intervalo após terminar cada execução;
`atraso-minimo` determina a idade mínima da operação, inclusive nas chamadas
diretas do serviço. Pode ser `PT0S`; valores negativos são recusados. Configure
por `CENTRAL_PROCESSAMENTO_HABILITADO`, `CENTRAL_PROCESSAMENTO_INTERVALO` e
`CENTRAL_PROCESSAMENTO_ATRASO_MINIMO`. Por padrão há pelo menos dez segundos
para observar PENDENTE; o processamento efetivo depende do próximo ciclo.

Cada ciclo seleciona até 100 operações pendentes antigas, por data/protocolo.
Cada operação tem transação independente: bloqueia a operação para escrita,
reconfere status/idade e bloqueia suas parcelas em ordem de ID. Revalida o
beneficiário, o status DISPONIVEL, a data de desconto >= hoje UTC e autorização
ATIVA para cada convênio. Autorizações consultadas permanecem bloqueadas para
leitura até o fim da transação. A operação válida fica CONFIRMADA; a inválida
fica REJEITADA, com motivo determinístico (parcela indisponível ou ausência de
autorização ativa). Ambas recebem `dataProcessamento`. Não há sorteio, integração
externa ou alteração de estado pelo GET/POST.

A V7 preserva `operacoes_parcelas` como histórico via `@ElementCollection` e
migra vínculos pendentes/confirmados para `reservas_parcelas`. A chave primária
por parcela impede duas reservas concorrentes; a FK composta obriga a existência
do vínculo histórico. O POST insere reservas, sem sobrescrever reservas existentes.
Na confirmação elas permanecem; na rejeição são removidas na mesma transação
que finaliza a operação. O histórico não é removido. A parcela rejeitada pode
ser tentada novamente quando elegível, com outra chave de idempotência.
A mesma chave original continua retornando o protocolo e o estado atual da
operação original, mesmo após outra tentativa. A agenda exclui parcelas com
reserva ativa. O status base da parcela permanece DISPONIVEL; a reserva define
a indisponibilidade durante PENDENTE e após CONFIRMADA.

Execuções concorrentes serializam pela operação e não reprocessam estados finais.
Uma falha técnica causa rollback, preservando PENDENTE e suas reservas para nova
tentativa. O erro com protocolo é registrado e o ciclo continua nas demais
operações. Decisão de modelagem: `docs/adr/0002-processamento-e-reserva-de-parcelas.md`.

Nos testes o agendador fica desabilitado; `ProcessamentoOperacaoService.processar`
e `processarPendentes` são chamados diretamente com relógio fixo e criação
persistida ajustada. Não há `Thread.sleep()` nem espera por agendamento.

```sh
cd central-convenios
./mvnw -Dtest=OperacaoIntegracaoTest,MigracaoReservasIntegracaoTest test
./mvnw test
```

Além do contrato HTTP, os testes cobrem atraso mínimo, confirmação, rejeição,
liberação e nova tentativa com histórico, agenda, idempotência finalizada,
concorrência do POST e do processador, rollback com falha real no commit e
migração incremental com vínculos prévios e restrições de integridade.
