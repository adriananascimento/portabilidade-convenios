# ADR-0004 — Atualização assíncrona dos status das operações de portabilidade

## Contexto

A Central de Convênios processa as operações de portabilidade de forma assíncrona. Após o registro, uma operação pode permanecer pendente até ser confirmada, rejeitada ou encerrada com falha técnica.
O Portal precisa acompanhar essas alterações e atualizar a interface automaticamente, sem exigir que o usuário recarregue a página.
Como a Central disponibiliza uma API HTTP para consulta individual por protocolo, foi necessário definir uma estratégia para acompanhar os status das operações.

## Decisão

Utilizar **polling no frontend React**, realizando consultas periódicas ao backend do Portal, que fica responsável por consultar a Central e atualizar os dados locais.
O polling segue as seguintes regras:

- Consultar apenas operações da página exibida que possuem protocolo e status `PENDENTE` ou ainda não definido.
- Realizar a primeira consulta após 10 segundos.
- Aguardar 10 segundos após a conclusão de cada ciclo para iniciar o próximo.
- Executar as requisições de forma sequencial, evitando consultas simultâneas na mesma instância.
- Interromper o polling quando não houver operações elegíveis.
- Cancelar consultas pendentes ao trocar de página ou sair da tela.
- Atualizar apenas as operações que tiveram alteração de status, sem recarregar toda a listagem.

O status `FALHA_TECNICA` não entra no polling automático, pois a Central não realiza novas transições a partir desse estado.
Em caso de erro na consulta, a interface mantém o último status conhecido e tenta novamente no próximo ciclo.
O intervalo do polling é configurável, com valor padrão de 10 segundos.

## Justificativa

O polling foi escolhido por atender à necessidade de atualização automática sem exigir alterações no contrato HTTP da Central ou infraestrutura adicional.
O controle das requisições no frontend evita consultas desnecessárias enquanto a tela está ativa.

## Alternativas consideradas

**Polling centralizado no backend com SSE:** permitiria centralizar as consultas e notificar os clientes quando houvesse alterações, mas exigiria gerenciamento de conexões e um mecanismo de sincronização independente da interface.
**Consulta incremental por data:** reduziria a quantidade de consultas individuais, mas exigiria um endpoint específico na Central, controle de cursor e tratamento para evitar perda de atualizações.
**Consulta em lote por protocolos:** permitiria consultar várias operações em uma única requisição, reduzindo o tráfego HTTP. É uma alternativa viável caso o volume de operações aumente, mas exigiria ampliar o contrato da Central.
**Mensageria com RabbitMQ ou Kafka:** permitiria que a Central publicasse eventos de alteração de status, mas exigiria infraestrutura adicional e suporte da Central à publicação desses eventos.

## Consequências

**Positivas:**

- Implementação simples, utilizando os endpoints HTTP existentes.
- Atualização automática da interface.
- Sem necessidade de infraestrutura adicional.
- Consultas limitadas às operações que precisam de acompanhamento.
- Possibilidade de evoluir para outras estratégias de sincronização.

**Negativas:**

- A atualização não é imediata e depende do intervalo configurado.
- Múltiplas abas ou usuários podem consultar a mesma operação.
- O acompanhamento automático depende de uma tela ativa.
- Falhas de comunicação podem atrasar a atualização do status.

O cancelamento de uma requisição no frontend evita que respostas antigas alterem a interface, mas não garante que o processamento já iniciado no backend seja interrompido.
