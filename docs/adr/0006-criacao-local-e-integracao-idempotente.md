# ADR-0006 — Criação local e integração idempotente

## Decisão aprovada

O POST `/api/operacoes` registra a solicitação localmente como `PENDENTE_ENVIO`
antes de chamar a Central, usando a mesma chave UUID recebida no header.
Beneficiário, conta e conjunto de parcelas identificam o conteúdo; a ordem das
parcelas é irrelevante e identificadores repetidos são rejeitados.

A constraint única da chave define qual requisição pode enviar: somente aquela
que confirmou a inserção nesta execução. Uma disputa de inserção é tratada após
o rollback, consultando o registro vencedor em uma nova transação. Não há locks
em memória nem transação ou lock de banco mantido durante o HTTP. O service
rejeita chamadas dentro de transação preexistente (`Propagation.NEVER`).

Repetição com conteúdo divergente retorna 409. Repetição de operação enviada
retorna 202 com protocolo, status oficial e data de criação da Central persistidos.
A V2 acrescenta essa data sem substituir a data de criação local.
Operações existentes sem confirmação não são reenviadas: retornam 503 com o
estado local. Falhas de integração também retornam 503, sem expor conteúdo remoto.

Rejeições HTTP 4xx (exceto 408) e conexão comprovadamente não estabelecida são
`FALHA_ENVIO`. Timeout, 408, 5xx, resposta inválida e falhas de leitura são
`ENVIO_INDETERMINADO`, pois não comprovam ausência de processamento remoto.
O sucesso atualiza protocolo, data remota, `ENVIADA` e status oficial, usando
`PENDENTE` se o status não vier preenchido.

`FALHA_TECNICA` pertence ao domínio oficial: a Central o atribui em
`OperacaoPortabilidade.registrarFalha` quando encerra seu processamento sem
sucesso, e `OperacaoRespostaDTO.de` expõe o status persistido. O POST idempotente
pode devolver esse estado após o processamento. A V2 estende o CHECK local para
aceitá-lo. Ele nunca é atribuído por falhas de comunicação do backend.

## Limitações deliberadas

`PENDENTE_ENVIO` e `ENVIO_INDETERMINADO` podem ficar paradas após falhas ou
reinicialização, inclusive se o processo cair entre a inserção e o HTTP, ou entre
a resposta remota e a atualização local. Se o banco falhar ao guardar o sucesso,
tenta-se registrar `ENVIO_INDETERMINADO` em nova transação; se também falhar,
pode permanecer `PENDENTE_ENVIO`. O registro inicial não é revertido.

Repetir uma solicitação não retoma o envio, inclusive em `FALHA_ENVIO`.
Recuperação com retry e backoff será implementada posteriormente. Não há retry
automático, polling, auditoria ou reconciliação nesta etapa. Respostas idempotentes
refletem o último status local, sem buscar atualizações na Central. A listagem
continua consultando a Central.

## Validação

Testes usam PostgreSQL e migrations reais em schema isolado. Duas requisições
são sincronizadas antes da inserção para disputar a mesma chave. Há cobertura
para conteúdos iguais e divergentes, sucesso, repetição, falhas e ausência de
transação durante o HTTP. Um teste do client usa servidor HTTP local para
verificar chave, payload, resposta 202 e desserialização do status oficial.
