# ADR-0005 — Persistência local das operações de portabilidade

## Contexto

O backend passará a manter seu histórico em PostgreSQL próprio, independente da
Central. Esta etapa cria somente a estrutura; os fluxos HTTP existentes continuam
sem gravar ou consultar operações locais. A modelagem foi aprovada pelo usuário,
com a orientação de separar o status da integração do status oficial.

## Decisão

- `OperacaoPortabilidade` possui UUID local, chave de idempotência UUID única,
  protocolo da Central UUID inicialmente nulo e identificador do beneficiário.
- As parcelas são um `Set<String>` com `@ElementCollection`. A tabela associada
  tem chave primária composta por operação e parcela e FK somente para a operação
  local. Não copia valores, autorizações ou reservas. Uma parcela pode constar
  em várias operações do histórico, sem duplicação dentro da mesma operação.
- `ContaDestino` é um `@Embeddable`: banco, agência e conta ficam na operação,
  preservando os dados da solicitação sem um cadastro independente de contas.
- `StatusIntegracao` contém `PENDENTE_ENVIO`, `ENVIADA`, `FALHA_ENVIO` e
  `ENVIO_INDETERMINADO`. A operação nasce em `PENDENTE_ENVIO`.
- `StatusOperacao` contém `PENDENTE`, `CONFIRMADA` e `REJEITADA`. É inicialmente
  nulo, assim como o protocolo, enquanto não houver confirmação de recebimento.
- Os enums são persistidos como texto com CHECKs. As datas usam `OffsetDateTime`
  em UTC e `TIMESTAMP WITH TIME ZONE`, inicializadas por `@PrePersist` e
  atualizadas por `@PreUpdate`. Não são um mecanismo de auditoria.
- A migration V1 cria o esquema e o Hibernate mantém `ddl-auto: validate`.

## Consequências e limites

Não há comunicação entre bancos nem cópia das entidades da Central. A constraint
única protege contra chaves duplicadas, mas não implementa o comportamento
idempotente do fluxo HTTP. Não são implementadas transições de estado,
reconciliação, polling, auditoria ou integração. A aplicação dos estados e o
tratamento de resultados oficiais fora deste enum exigirão definição futura.

Os testes usam o PostgreSQL já configurado, sem novas dependências, com migration
em schema exclusivo de nome aleatório, transações revertidas e remoção do schema
ao terminar. As credenciais de teste precisam permitir criação e remoção de schema.
