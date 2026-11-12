# Portabilidade de Convênios

## Central de Convênios: registro de autorização

A aplicação `central-convenios` é independente do backend e atende na porta 8081.
Requisitos: Java 21, Docker com Compose e acesso às dependências Maven.

```sh
cd central-convenios
docker compose up -d --wait
./mvnw spring-boot:run
```

O Flyway aplica as migrações V1 (autorizações), V2 (parcelas) e V3 (dados fictícios) automaticamente. O Hibernate apenas valida
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
