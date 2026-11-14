# ADR-0008 — Auditoria persistente das operações

## Contexto e decisão

Proposta aprovada pelo usuário antes da implementação. Registrar acontecimentos
observados em `eventos_operacao`, associados ao identificador local da operação.
A entidade `EventoOperacao` é imutável no Hibernate, não oferece setters e seu
repository expõe somente inserção e consulta. Não há cascata de exclusão nem
endpoints para alterar ou excluir eventos. A proteção se aplica ao fluxo normal;
SQL administrativo ainda pode alterar os registros.

Eventos: `OPERACAO_CRIADA_LOCALMENTE`, `TENTATIVA_ENVIO_CENTRAL`,
`PROTOCOLO_RECEBIDO`, `FALHA_INTEGRACAO` e `RESULTADO_INDETERMINADO`.
Status anteriores/novos são opcionais e registram transições históricas; o
protocolo é registrado no recebimento. Não copiar payloads, dados bancários ou
mensagens brutas de exceções para a auditoria.

## Transações e limites

A criação local e seu evento compartilham a primeira transação. A chamada HTTP
continua fora de transação. Depois dela, a atualização do estado e os eventos de
tentativa e resultado compartilham outra transação. Uma falha reverte todo o
conjunto local. A chave única de idempotência decide qual criação pode prosseguir;
repetições não fazem nova chamada nem inserem eventos.

`TENTATIVA_ENVIO_CENTRAL` é persistido **após** a tentativa HTTP. Não comprova
recebimento pela Central e pode não existir se o processo for interrompido durante
a comunicação ou antes do commit. A data do evento é a data do registro local,
não o instante exato de envio ou a data de criação informada pela Central.

Falhas de comunicação preservam a classificação existente de integração.
Se a Central respondeu com sucesso e a persistência do protocolo falhar, uma
transação de recuperação tenta registrar `ENVIO_INDETERMINADO`, tentativa e
resultado indeterminado, explicitando a falha de persistência local. Não é retry
HTTP. Se essa transação também falhar, permanece o último conjunto confirmado;
a exceção de persistência adicional é preservada como suprimida. HTTP e banco
não têm atomicidade distribuída; nenhuma garantia de auditoria completa existe
quando o processo ou o banco falha antes do commit.

## Consulta

`GET /api/operacoes/{id}/eventos`: lista de DTOs, ordenada por
`dataCriacao ASC, id ASC`, com índice correspondente. UUID desempata datas iguais
de forma estável, sem representar ordem causal dentro desse empate. Operação
inexistente retorna 404; operação existente sem histórico retorna lista vazia.
Não reconstruir eventos de operações anteriores à migration V3.

Central, frontend, polling, sincronização e retry automático ficam fora do escopo.
