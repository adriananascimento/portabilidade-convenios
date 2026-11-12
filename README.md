# Portabilidade de Convênios

## Central de Convênios: registro de autorização

A aplicação `central-convenios` é independente do backend e atende na porta 8081.
Requisitos: Java 21, Docker com Compose e acesso às dependências Maven.

```sh
cd central-convenios
docker compose up -d --wait
./mvnw spring-boot:run
```

O Flyway aplica as migrações V1 (autorizações), V2 (parcelas), V3 (dados fictícios)
e V4 (operações de portabilidade) automaticamente. O Hibernate apenas valida
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
com autorização `ATIVA` para o mesmo convênio e data de desconto maior ou igual a
hoje são retornadas, em ordem crescente de data (desempate por identificador).
A data de referência usa UTC. Sem parcelas elegíveis, inclusive para beneficiário
sem registros, retorna `parcelas: []`.

A V2 cria `parcelas` com identificadores textuais, valor `NUMERIC(15,2)`, data
`DATE` e status `DISPONIVEL`, único estado necessário neste escopo. A consulta não
adiciona filtro por status da parcela. O índice por beneficiário e data auxilia
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
estrangeiras para operação/parcela e unicidade da parcela. A chave de idempotência
também é única. O Service usa transação independente, disputa a chave primeiro e
bloqueia parcelas em ordem de identificador para evitar reservas concorrentes e
ciclos de locks. As restrições do banco também protegem contra duplicidade;
a recuperação da chave vencedora ocorre depois do rollback da transação perdedora.
O vínculo é uma coleção JPA de identificadores persistida na tabela relacional,
sem expor entidades na API. Não há novas dependências.

Todas as operações permanecem `PENDENTE`. A unicidade da parcela é permanente
nesta etapa, pois não há operações encerradas nem liberação de reservas. Futuras
transições de estado exigirão rever essa restrição. O endpoint não altera o
comportamento da agenda ou das autorizações e não processa operações nem oferece GET.

```sh
cd central-convenios
./mvnw -Dtest=OperacaoIntegracaoTest test
```

A suíte de operações usa HTTP e PostgreSQL via Testcontainers e cobre criação,
validações, rollback, autorizações por convênio, idempotência e concorrência com
chaves iguais e diferentes. Para todas as suítes, execute `./mvnw test`, mantendo
o banco local disponível para o teste de contexto existente.
