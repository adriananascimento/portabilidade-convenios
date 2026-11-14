# ADR-004 — Atualização assíncrona dos status das operações de portabilidade

## Contexto

A Plataforma de Portabilidade integra-se à Central de Convênios, um sistema externo e independente, responsável pelo processamento assíncrono das operações.

Após o registro de uma operação, a Central realiza seu processamento e atualiza o status para CONFIRMADA ou REJEITADA.

A aplicação precisa acompanhar essas mudanças e atualizar a interface automaticamente, sem exigir que o usuário recarregue a página.

A Central disponibiliza uma API HTTP para consulta individual de operações por protocolo.

Foram avaliadas diferentes estratégias de sincronização, considerando desacoplamento, consistência, complexidade de implementação e possibilidade de evolução.

## Decisão

Será utilizado **polling no frontend React**, por meio de consultas periódicas ao backend de Portabilidade.

O backend será responsável por consultar a Central de Convênios e retornar o status atualizado das operações.

O frontend realizará consultas apenas enquanto houver operações pendentes que precisem de acompanhamento.

A frequência das consultas será configurável, permitindo ajustes conforme a necessidade.

A implementação deverá evitar requisições simultâneas desnecessárias e interromper o polling quando o componente for desmontado.

## Alternativas consideradas

### Polling no backend com SSE

Um job agendado no backend consultaria periodicamente a Central, persistiria as alterações e notificaria o React por Server-Sent Events.

**Vantagens:**
- Centralização da sincronização.
- Menor quantidade de consultas originadas por múltiplos clientes.
- Atualização da interface por eventos.

**Desvantagens:**
- Necessidade de gerenciamento de conexões SSE.
- Maior complexidade de implementação e operação.
- Necessidade de um mecanismo de sincronização independente da interface.

**Decisão:** não adotado nesta versão, devido ao escopo e à complexidade adicional.

### Consulta incremental por data de processamento

A Central disponibilizaria um endpoint para retornar operações processadas após determinada data.

Foram consideradas duas possibilidades:
- Utilização de `dataProcessamento`, campo já existente na Central.
- Introdução de `dataAtualizacao` para acompanhar diferentes alterações de estado.

**Vantagens:**
- Redução de consultas individuais.
- Possibilidade de sincronização incremental.
- Menor transferência de dados em cenários com grande volume de operações.

**Desvantagens:**
- Necessidade de persistir um cursor de sincronização.
- Necessidade de paginação e ordenação estável.
- Risco de perda de alterações em cenários de transações concorrentes, caso o cursor seja baseado apenas em timestamps.
- Maior complexidade para garantir recuperação após falhas.

**Decisão:** não adotado nesta versão.

### Consulta em lote por protocolos

O backend enviaria à Central uma lista de protocolos pendentes e receberia seus respectivos status.

**Vantagens:**
- Redução do número de requisições HTTP.
- Não exige controle de sincronização por data.
- Permite consultar apenas operações ainda pendentes.
- Facilita novas tentativas após falhas.

**Desvantagens:**
- Necessidade de ampliar o contrato HTTP da Central.
- Necessidade de definir limites de lote e tratamento de respostas parciais.

**Decisão:** considerada uma evolução viável caso o volume de operações aumente.

### Mensageria com RabbitMQ ou Kafka

A Central publicaria eventos de alteração de status, consumidos pelo backend de Portabilidade.

**Vantagens:**
- Comunicação assíncrona entre sistemas.
- Desacoplamento temporal.
- Possibilidade de processamento confiável com mecanismos adequados de persistência e confirmação.

**Desvantagens:**
- Infraestrutura adicional.
- Necessidade de um contrato de eventos disponibilizado pela Central.
- Maior complexidade operacional.

**Decisão:** não adotado nesta versão, pois a integração disponível é baseada em HTTP.

## Consequências

### Positivas
- Implementação simples e compatível com a arquitetura existente.
- Reutilização dos endpoints HTTP.
- Ausência de infraestrutura adicional.
- Facilidade de testes e manutenção.
- Possibilidade de evolução incremental.

### Negativas e limitações
- A atualização não é instantânea e depende do intervalo de polling.
- Múltiplos clientes podem gerar consultas repetidas.
- O acompanhamento automático depende de uma interface ativa.
- É necessário controlar requisições concorrentes e falhas de comunicação.
- O backend deve preservar o desacoplamento em relação à Central.

## Evoluções futuras

Caso os requisitos de escala ou latência sejam ampliados, poderão ser consideradas:

1. Consulta em lote por protocolos pendentes.
2. Polling centralizado no backend.
3. SSE para notificação dos clientes.
4. Sincronização incremental com cursor persistido.
5. Mensageria com RabbitMQ ou Kafka, caso a Central disponibilize eventos de domínio.

## Justificativa

A estratégia de polling no frontend foi escolhida por atender aos requisitos funcionais com menor complexidade de implementação.

A decisão considera o escopo atual da aplicação, preservando a integração HTTP e a independência da Central de Convênios.

As alternativas avaliadas permanecem documentadas como possibilidades de evolução, caso surjam novos requisitos de escalabilidade, disponibilidade ou atualização em tempo real.
