# ADR-0006 — Auditoria persistente das operações

## Contexto

O Portal precisa manter um histórico dos acontecimentos relacionados às operações de portabilidade, desde a criação local até a comunicação com a Central de Convênios.
Armazenar apenas o status atual não permite identificar quais etapas foram executadas, se houve falhas de integração ou quando determinado resultado foi recebido.
Por isso, foi necessário implementar um registro de eventos para acompanhar o histórico das operações e facilitar a análise de possíveis problemas.

## Decisão

Criar uma tabela `eventos_operacao` para armazenar os eventos associados a cada operação de portabilidade.
Os eventos são registrados de forma imutável pela aplicação, sem permitir alterações ou exclusões pelos fluxos normais do sistema.
Foram definidos os seguintes tipos de evento:
- `OPERACAO_CRIADA_LOCALMENTE`: registro da solicitação no Portal.
- `TENTATIVA_ENVIO_CENTRAL`: tentativa de envio da operação à Central.
- `PROTOCOLO_RECEBIDO`: recebimento do protocolo da Central.
- `FALHA_INTEGRACAO`: falha identificada durante a integração.
- `RESULTADO_INDETERMINADO`: situação em que não é possível confirmar o resultado do envio.
Os eventos podem armazenar os status anteriores e novos da integração, além do protocolo recebido.
Não são armazenados payloads completos, dados bancários ou mensagens brutas de exceções, evitando expor informações sensíveis no histórico.

## Justificativa

O registro de eventos permite acompanhar o que aconteceu com cada operação, mesmo quando seu status atual não apresenta todas as informações necessárias.
A persistência separada das tentativas de envio também facilita identificar falhas durante a comunicação com a Central.
A decisão de não armazenar dados sensíveis ou mensagens completas de erro reduz o risco de exposição dessas informações.

## Alternativas consideradas

**Armazenar apenas o status atual:** seria mais simples, mas não permitiria consultar o histórico de alterações e tentativas de integração.
**Registrar os acontecimentos apenas em logs:** ajudaria no diagnóstico, mas dificultaria a consulta do histórico diretamente pela operação e dependeria da retenção dos logs.
**Armazenar payloads completos das requisições e respostas:** ofereceria mais detalhes para análise, mas aumentaria o armazenamento e o risco de exposição de dados sensíveis.

## Consequências

**Positivas:**

- Histórico dos principais acontecimentos de cada operação.
- Maior facilidade para investigar falhas de integração.
- Consulta dos eventos diretamente pelo identificador da operação.
- Preservação do histórico de tentativas de envio.
- Menor exposição de informações sensíveis.

**Negativas:**

- Necessidade de armazenar e consultar registros adicionais.
- Maior complexidade no controle das transações.
- O histórico pode ficar incompleto em caso de falha no banco ou interrupção da aplicação.

A imutabilidade é garantida pelas regras da aplicação, mas não impede alterações realizadas diretamente no banco por usuários com privilégios administrativos.
Também não existe uma transação distribuída entre o Portal e a Central. Portanto, um evento de tentativa não comprova que a solicitação foi recebida ou processada pela Central.
