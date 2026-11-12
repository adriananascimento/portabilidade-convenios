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
