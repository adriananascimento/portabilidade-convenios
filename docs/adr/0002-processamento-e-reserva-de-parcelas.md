# ADR-0002 — Processamento e reserva de parcelas

## Contexto

Na Central de Convênios, uma parcela pode fazer parte de diferentes tentativas de portabilidade, desde que não esteja reservada por outra operação ativa.
Inicialmente, a tabela `operacoes_parcelas` possuía uma restrição de unicidade por parcela. Isso impedia que uma parcela fosse utilizada novamente após a rejeição de uma operação.
Remover o vínculo da parcela com a operação rejeitada resolveria a restrição, mas causaria perda de histórico e poderia comprometer a validação de idempotência.
Também era necessário garantir que duas operações concorrentes não conseguissem reservar a mesma parcela.

## Decisão

Manter a tabela `operacoes_parcelas` como histórico das parcelas vinculadas a cada operação e criar uma tabela separada para controlar as reservas ativas.
Cada parcela pode possuir apenas uma reserva ativa, garantindo que não seja utilizada simultaneamente em operações diferentes.
As reservas seguem as seguintes regras:
- Ao registrar uma operação, suas parcelas são reservadas.
- Quando a operação é confirmada, as reservas são mantidas.
- Quando a operação é rejeitada, as reservas são liberadas.
- A consulta da agenda considera disponíveis apenas as parcelas que atendem às regras de negócio e não possuem reserva ativa.
O processamento das operações é realizado de forma assíncrona, por meio de um agendador `@Scheduled`, com intervalo e atraso mínimo configuráveis.
Para evitar problemas de concorrência, cada operação é processada em sua própria transação, utilizando bloqueio pessimista e revalidação dos dados antes da confirmação.
Durante o processamento, são verificadas as condições das parcelas, a data do desconto e a autorização do beneficiário para o convênio.
Se alguma regra de negócio não for atendida, a operação é rejeitada com o motivo correspondente.
As operações que já possuem status final não são processadas novamente.

## Justificativa

A separação entre histórico e reservas permite manter o vínculo original das parcelas com cada operação, sem impedir novas tentativas após uma rejeição.
O controle das reservas pelo banco de dados evita que operações concorrentes utilizem a mesma parcela.
O processamento assíncrono também permite que o registro da portabilidade seja concluído sem aguardar sua confirmação.
O uso de transações independentes por operação evita que uma falha no processamento de uma solicitação comprometa as demais.

## Alternativas consideradas

**Manter a unicidade na tabela de histórico:** impediria novas tentativas de portabilidade para parcelas vinculadas a operações rejeitadas.
**Remover os vínculos após a rejeição:** permitiria reutilizar as parcelas, mas causaria perda de histórico e prejudicaria a idempotência.
**Transformar os vínculos em entidades próprias:** permitiria controlar mais informações por vínculo, mas aumentaria a complexidade do modelo sem necessidade para o cenário atual.

## Consequências

**Positivas:**

- Preservação do histórico das parcelas em todas as operações.
- Possibilidade de reutilizar parcelas após a rejeição.
- Controle de concorrência para evitar reservas duplicadas.
- Manutenção da idempotência mesmo após a finalização das operações.
- Processamento independente das solicitações.

**Negativas:**

- Necessidade de uma tabela adicional para controlar reservas.
- Maior complexidade nas transações e no tratamento de concorrência.
- Necessidade de testes de integração para validar bloqueios, rollback e integridade dos dados.
