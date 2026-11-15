# ADR-0007 — Sincronização do status oficial das operações

## Contexto

O processamento das operações de portabilidade acontece de forma assíncrona na Central de Convênios.
Por isso, uma operação pode permanecer com status `PENDENTE` no Portal mesmo depois de ter sido confirmada ou rejeitada pela Central.
Foi necessário implementar uma forma de consultar o status oficial e atualizar o registro local, garantindo que consultas simultâneas não sobrescrevam informações mais recentes.

## Decisão

Disponibilizar o endpoint `POST /api/operacoes/{id}/sincronizar-status`, responsável por consultar o status oficial da operação na Central e atualizar o registro local quando necessário.
O endpoint recebe o identificador local da operação e utiliza o protocolo armazenado para consultar `GET /operacoes-portabilidade/{protocolo}` na Central.
Quando o status retornado é igual ao armazenado, nenhuma atualização é realizada.
Quando existe uma transição válida, o Portal atualiza o status da operação e registra o evento `STATUS_OPERACAO_ATUALIZADO`, armazenando os valores anterior e novo.
O status da integração não é alterado durante a sincronização.

### Controle de concorrência

Utilizar concorrência otimista com `@Version` na entidade `OperacaoPortabilidade`.
A consulta à Central é realizada fora de uma transação de banco.
Após receber a resposta, uma nova transação verifica se a operação ainda possui a mesma versão consultada inicialmente.
Se não houve alteração concorrente, o novo status é persistido junto ao evento de auditoria.
Se outra requisição atualizou a operação, o Portal consulta novamente o estado local:
- Se o status atual já corresponde ao recebido, retorna a operação sem realizar outra atualização.
- Se a operação já está em um estado final, preserva esse estado.
- Caso exista um conflito que não possa ser resolvido, retorna HTTP `409`.
Dessa forma, uma resposta antiga da Central não sobrescreve uma atualização mais recente identificada pelo controle de versão.

## Justificativa

Manter uma cópia local do status permite que o Portal apresente as operações sem depender de consultas à Central a cada carregamento da página.
A sincronização atualiza essa informação quando necessário, respeitando o resultado oficial do processamento.
O uso de `@Version` evita que consultas concorrentes sobrescrevam alterações já realizadas, sem manter bloqueios ou transações abertas durante a chamada HTTP.
O registro de eventos permite acompanhar as mudanças de status de cada operação.

## Alternativas consideradas

**Consultar a Central sempre que uma operação for exibida:** manteria os dados mais próximos do estado oficial, mas aumentaria a dependência da disponibilidade da Central.
**Manter bloqueios no banco durante a consulta HTTP:** evitaria algumas disputas de atualização, mas manteria recursos do banco ocupados durante a comunicação externa.
**Atualizar o status sem controle de versão:** seria mais simples, mas permitiria que respostas concorrentes sobrescrevessem informações mais recentes.

## Consequências

**Positivas:**

- Atualização do status local a partir da informação oficial da Central.
- Proteção contra atualizações concorrentes.
- Reutilização do mesmo endpoint pela atualização manual e pelo polling.
- Registro das alterações de status no histórico da operação.
- Preservação dos dados locais quando a Central está indisponível.

**Negativas:**

- Possibilidade de o status local ficar temporariamente desatualizado.
- Maior complexidade no tratamento de concorrência.
- Consultas simultâneas podem gerar chamadas HTTP redundantes à Central.

A sincronização não garante que o status permaneça atualizado após a resposta, pois o processamento na Central pode continuar evoluindo.
Também não existe uma versão temporal fornecida pela Central. O controle com `@Version` protege contra alterações concorrentes no Portal, mas não permite identificar todas as mudanças que possam ocorrer remotamente.
