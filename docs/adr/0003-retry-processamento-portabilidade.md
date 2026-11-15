# ADR-0003 — Retry no processamento de portabilidade

## Contexto

O processamento das operações de portabilidade é realizado de forma assíncrona pela Central de Convênios.
Inicialmente, quando ocorria uma falha técnica, a operação continuava pendente e era processada novamente a cada execução do agendador, sem limite de tentativas ou intervalo entre elas.
Esse comportamento poderia causar tentativas desnecessárias e sobrecarregar o sistema caso o problema persistisse.
Era necessário controlar as tentativas de processamento e diferenciar falhas temporárias de erros que não deveriam ser repetidos.

## Decisão

Implementar um mecanismo de retry com limite de cinco tentativas de processamento por operação.
Para isso, foram adicionados os seguintes campos:
- `tentativas_processamento`: quantidade de tentativas realizadas.
- `proxima_tentativa_em`: data e hora em que a operação poderá ser processada novamente.
- `ultimo_erro`: categoria da última falha técnica.
As falhas transitórias são repetidas com intervalos progressivos de **5, 10, 20 e 40 segundos**, sem bloquear a execução do agendador.
São consideradas transitórias as falhas de conexão com o banco, deadlocks, conflitos de serialização e indisponibilidade temporária de bloqueios.
Falhas técnicas não classificadas como transitórias encerram o processamento imediatamente com status `FALHA_TECNICA`.
Quando uma operação atinge a quinta tentativa sem sucesso, também passa para `FALHA_TECNICA`, sem novas tentativas automáticas.
As rejeições por regras de negócio continuam sendo tratadas com status `REJEITADA` e não entram no mecanismo de retry técnico.
O processamento continua utilizando transações independentes e bloqueios pessimistas para evitar alterações concorrentes na mesma operação.
Quando ocorre uma falha técnica, a transação de processamento é revertida e a tentativa é registrada em uma nova transação. Antes desse registro, o estado da operação é consultado novamente para evitar sobrescrever uma confirmação realizada por outra execução.
As reservas das parcelas são mantidas em caso de falha técnica, preservando os dados para análise ou possível reconciliação posterior.

## Justificativa

O limite de tentativas evita que operações com falhas persistentes sejam processadas indefinidamente.
Os intervalos progressivos permitem que problemas temporários sejam resolvidos antes de uma nova tentativa, sem manter threads aguardando.
A separação entre falhas técnicas e rejeições de negócio também evita repetir operações que já foram rejeitadas por uma regra da Central.
O registro das tentativas em uma transação separada permite preservar as informações da falha mesmo quando a transação principal precisa ser revertida.

## Alternativas consideradas

**Repetir o processamento em todos os ciclos do agendador:** solução mais simples, mas sem controle sobre a quantidade de tentativas ou o intervalo entre elas.
**Repetir qualquer erro técnico:** poderia gerar novas tentativas para erros que não seriam resolvidos automaticamente.
**Utilizar espera entre tentativas na mesma execução:** manteria threads ocupadas desnecessariamente. Por isso, foi escolhido persistir o horário da próxima tentativa.

## Consequências

**Positivas:**

- Controle da quantidade de tentativas por operação.
- Menor risco de processamento repetitivo em caso de falhas persistentes.
- Tratamento diferente para falhas transitórias e permanentes.
- Preservação das informações necessárias para diagnóstico.
- Proteção contra sobrescrita de operações concluídas por outra execução.

**Negativas:**

- Maior complexidade no controle de estados e transações.
- Necessidade de persistir informações adicionais de processamento.
- Possibilidade de uma tentativa não ser registrada caso ocorra uma falha entre o rollback e a gravação do erro.

As mensagens persistidas em `ultimo_erro` são categorias genéricas, evitando armazenar dados sensíveis ou detalhes internos das exceções.

O mecanismo não garante o registro de todas as execuções físicas em situações de falha ou concorrência. Além disso, operações encerradas com `FALHA_TECNICA` não possuem recuperação automática.

## Validação

Foram previstos testes de integração com PostgreSQL/Testcontainers para verificar:

- Limite de tentativas e intervalos de retry.
- Classificação de falhas transitórias e permanentes.
- Rollback e persistência das tentativas.
- Concorrência entre execuções.
- Preservação de operações já concluídas.

Os testes utilizam controle de tempo com `Clock` e mecanismos de sincronização para evitar dependência de esperas reais.

## ADRs relacionados

- [ADR-0002 — Processamento e reserva de parcelas](0002-processamento-e-reserva-de-parcelas.md)
