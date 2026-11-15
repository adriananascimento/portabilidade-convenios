# ADR-0008 — Retry no envio inicial à Central

## Contexto

Durante o envio de uma operação de portabilidade à Central de Convênios, podem ocorrer falhas temporárias, como timeouts, problemas de conexão ou indisponibilidade do serviço.
Sem um mecanismo de retry, essas falhas encerrariam a integração imediatamente, mesmo quando uma nova tentativa poderia ser bem-sucedida.
Foi necessário definir uma estratégia para repetir o envio de forma controlada, sem criar novas operações locais ou gerar solicitações duplicadas na Central.

## Decisão

Implementar o retry diretamente no `CentralConveniosClient`, utilizando um laço explícito, sem adicionar dependências como Spring Retry.
O envio permite **até três tentativas**, com intervalos progressivos:

- Primeira tentativa: envio imediato.
- Segunda tentativa: após 1 segundo.
- Terceira tentativa: após mais 2 segundos.

O retry é realizado apenas para respostas HTTP `502`, `503` e `504`, ou falhas de transporte relacionadas à conexão e timeout. Outros erros não são repetidos.
O JSON da solicitação e a assinatura HMAC são gerados antes da primeira tentativa. Todas as tentativas reutilizam o mesmo conteúdo, a mesma chave de idempotência e a mesma assinatura.
Dessa forma, a Central consegue reconhecer as tentativas como parte da mesma solicitação.

### Registro das tentativas

Cada tentativa de envio gera um evento `TENTATIVA_ENVIO_CENTRAL`, persistido em uma transação independente.
O registro acontece após a execução da tentativa HTTP, inclusive quando ocorre uma falha de comunicação.
Se o registro do evento falhar, o processamento é interrompido, sem realizar novas tentativas.
A quantidade de eventos registrados representa o número de envios realizados, sem necessidade de manter um contador adicional.
A persistência do resultado final da integração acontece separadamente, sem gerar eventos de tentativa adicionais.

## Justificativa

Como a quantidade de tentativas e as regras de retry são pequenas, um laço explícito atende à necessidade sem adicionar complexidade ou dependências ao projeto.
O uso da mesma chave de idempotência em todas as tentativas reduz o risco de duplicidade quando a Central recebeu uma solicitação, mas o Portal não conseguiu obter sua resposta.
A persistência independente dos eventos permite manter o histórico das tentativas mesmo quando ocorre uma falha posterior na gravação do resultado.

## Alternativas consideradas

**Não realizar retry:** simplificaria a implementação, mas falhas temporárias poderiam impedir operações que seriam concluídas em uma nova tentativa.
**Utilizar Spring Retry:** ofereceria recursos adicionais de configuração, mas acrescentaria uma dependência desnecessária para o fluxo atual.
**Implementar retry assíncrono:** evitaria manter a requisição HTTP ocupada durante as tentativas, mas exigiria uma estrutura adicional de processamento e controle.

## Consequências

**Positivas:**

- Maior tolerância a falhas transitórias.
- Redução de falhas de integração causadas por indisponibilidades temporárias.
- Preservação da idempotência entre as tentativas.
- Registro individual de cada tentativa de envio.
- Implementação simples, sem dependências adicionais.

**Negativas:**

- A requisição HTTP permanece ocupada durante as tentativas e os intervalos de espera.
- O tempo adicional de espera pode chegar a 3 segundos, além do tempo gasto nas chamadas HTTP.
- Não existe recuperação automática após o encerramento da requisição original.

Como a comunicação HTTP e a persistência local não fazem parte de uma transação distribuída, ainda podem ocorrer situações em que a Central recebe a operação, mas o Portal não consegue persistir o resultado.
O histórico das tentativas também pode ficar incompleto se a aplicação for interrompida antes da confirmação da transação de auditoria.
