# ADR-0005 — Persistência local e integração idempotente das operações

## Contexto

O Portal precisa registrar as solicitações de portabilidade na Central de Convênios e manter um histórico próprio das operações realizadas.
Como a integração acontece por HTTP, podem ocorrer falhas de comunicação, timeouts ou situações em que a Central recebe a solicitação, mas o Portal não consegue obter a resposta.
Também é necessário garantir que requisições repetidas não criem operações duplicadas, principalmente quando existem chamadas concorrentes ou novas tentativas após uma falha.
Para atender a esses cenários, foi necessário definir como as operações seriam armazenadas localmente e como seria realizado o envio à Central.

## Decisão

Utilizar o PostgreSQL do Portal para armazenar as operações de portabilidade, mantendo a persistência independente da Central de Convênios.
Foram definidos dois controles de status: **StatusIntegracao** que representa a situação da comunicação entre o Portal e a Central e **StatusOperacao** que representa o resultado informado pela Central.
O status da operação e o protocolo começam nulos, enquanto não houver confirmação do recebimento pela Central.

### Criação e envio das operações

O fluxo segue as seguintes regras:

- A chave de idempotência possui uma restrição de unicidade no banco.
- Apenas a requisição que criou o registro local pode iniciar o envio.
- Requisições repetidas com a mesma chave e o mesmo conteúdo retornam a operação existente.
- Requisições com a mesma chave e conteúdo diferente retornam HTTP `409 Conflict`.
- A comparação considera beneficiário, conta de destino e parcelas selecionadas, independentemente da ordem.
- Identificadores de parcelas repetidos na mesma solicitação são rejeitados.

A transação de criação local é concluída antes da chamada HTTP, evitando manter transações ou bloqueios de banco durante a comunicação com a Central.
Quando a Central confirma o recebimento, o Portal armazena o protocolo, a data de criação remota e o status oficial, atualizando o status de integração para `ENVIADA`.

## Justificativa

A persistência local garante que o Portal mantenha o registro da solicitação mesmo quando ocorre uma falha de integração.
A persistência local também permite listar e consultar as operações diretamente no Portal, sem depender da disponibilidade da Central. A comunicação com a Central fica restrita às operações de integração e sincronização necessárias.
A restrição de unicidade da chave de idempotência permite controlar requisições concorrentes sem depender de bloqueios em memória.
Separar a transação local da chamada HTTP evita manter recursos do banco ocupados durante uma operação de rede.
A utilização da mesma chave de idempotência no Portal e na Central permite identificar tentativas repetidas como a mesma solicitação.
A separação entre `StatusIntegracao` e `StatusOperacao` também evita confundir problemas de comunicação com o resultado oficial do processamento.

## Alternativas consideradas

**Enviar primeiro à Central e persistir depois:** seria mais simples, mas poderia deixar o Portal sem registro local caso a persistência falhasse após o envio.
**Manter uma transação aberta durante a chamada HTTP:** manteria recursos do banco ocupados durante a comunicação externa, sem garantir atomicidade entre os dois sistemas.
**Controlar duplicidade apenas em memória:** não garantiria o comportamento em múltiplas instâncias da aplicação ou após uma reinicialização.
**Utilizar um único status para integração e operação:** simplificaria a modelagem, mas dificultaria distinguir falhas de comunicação dos resultados oficiais da portabilidade.

## Consequências

**Positivas:**

- Histórico das solicitações armazenado no Portal.
- Persistência independente da Central.
- Proteção contra operações duplicadas.
- Controle de concorrência por restrição de unicidade no banco.
- Separação entre transações locais e chamadas HTTP.
- Possibilidade de consultar o último estado conhecido da operação.

**Negativas:**

- Maior complexidade no controle dos estados da integração.
- Possibilidade de divergência temporária entre os dados do Portal e da Central.
- Necessidade de tratar situações em que o resultado do envio é desconhecido.

Atualmente, não existe recuperação automática dessas operações após o encerramento da requisição original.
A sincronização de status também depende de a operação possuir um protocolo conhecido da Central.
