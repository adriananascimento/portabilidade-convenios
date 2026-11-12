# Portabilidade de Convênios

## Central de Convênios: registro de autorização

A aplicação `central-convenios` é independente do backend e atende na porta 8081.
Requisitos: Java 21, Docker com Compose e acesso às dependências Maven.

```sh
cd central-convenios
docker compose up -d --wait
./mvnw spring-boot:run
```

O Flyway aplica `V1__criar_autorizacoes.sql` automaticamente. O Hibernate apenas valida
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

Os testes exercitam a API HTTP, JPA e Flyway em PostgreSQL real, sem mocks.
Utilize um banco exclusivo de testes; o padrão é a porta 55434 e o banco
`central_convenios_testes`, evitando o banco de desenvolvimento. Os testes
inserem registros com identificadores aleatórios; o contêiner abaixo não usa volume.

```sh
docker run --detach --rm --name central-autorizacoes-testes \
  -e POSTGRES_DB=central_convenios_testes \
  -e POSTGRES_USER=central -e POSTGRES_PASSWORD=central \
  -p 127.0.0.1:55434:5432 postgres:17
# Aguarde o banco ficar pronto; repita até obter sucesso:
docker exec central-autorizacoes-testes pg_isready -U central -d central_convenios_testes
# Na pasta central-convenios:
./mvnw test
# Ao terminar, descarte somente o contêiner de testes:
docker stop central-autorizacoes-testes
```

Para outro PostgreSQL de testes, defina `CENTRAL_TEST_DATABASE_URL` e, se necessário,
`SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`.
A suíte cobre criação, reenvio, validação e oito requisições concorrentes, exigindo
um único `201`, sete `200`, respostas iguais e apenas um registro para o par.
