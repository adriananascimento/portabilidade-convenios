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

## Implementação no frontend — 14/11/2026

Proposta aprovada pelo usuário, com ajuste explícito: para o polling automático,
`FALHA_TECNICA` é final, pois a Central não implementa saída desse estado. A
sincronização automática inclui apenas operações com protocolo e status
`PENDENTE` ou nulo, somente na página exibida. O backend permanece inalterado,
inclusive sua permissão para consultar manualmente `FALHA_TECNICA`.

O controlador do frontend usa `setTimeout` recursivo: primeira consulta após
10 segundos da carga e próximo ciclo 10 segundos depois da conclusão do
anterior. Requisições sequenciais e deduplicação por ID impedem sobreposição
na mesma instância. O intervalo é parâmetro do controlador, com padrão 10.000 ms.
Sem elegíveis, não há temporizador ativo.

Um AbortController identifica cada carga da listagem. Nova carga/paginação ou
cleanup do componente cancela temporizador e fetch e invalida respostas antigas,
inclusive quando o transporte entrega uma resposta após o abort. O abort não
garante cancelamento do processamento remoto. Abas distintas continuam podendo
consultar a mesma operação; a concorrência do backend segue conforme ADR-0009.

Respostas atualizam linhas por ID sem recarregar a listagem ou alterar metadados.
Falhas preservam o estado conhecido, não impedem consultas das demais operações
e geram aviso; o próximo ciclo tenta novamente. Cancelamentos não geram erro na
interface. Não foram adicionadas dependências nem alterados backend ou Central.
