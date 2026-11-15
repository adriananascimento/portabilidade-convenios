# ADR 0010 — Retry no envio inicial à Central

## Contexto e decisão

O envio inicial precisa tolerar falhas transitórias sem criar novas operações locais.
Usar laço explícito em `CentralConveniosClient`, sem Spring Retry ou dependências
adicionais: são apenas três envios com espera de 1s e 2s, restritos a 502/503/504
ou falhas de transporte (`ResourceAccessException` com causa de conexão ou timeout). Outros status não são repetidos.
O JSON e a assinatura são calculados antes do laço; chave, corpo e HMAC são reutilizados.
Resposta recebida encerra o laço; a validação do protocolo continua no serviço.

O serviço continua com `Propagation.NEVER` e usa `REQUIRES_NEW` para criação,
cada evento de tentativa e resultado final. O callback executado no `finally` de
cada envio registra inclusive falhas HTTP e de transporte, antes do backoff.
A quantidade de eventos `TENTATIVA_ENVIO_CENTRAL` é a contagem de envios; não há
contador redundante ou migração. Falha ao auditar interrompe o fluxo, sem outro envio.
Persistência do protocolo ou da falha não acrescenta uma tentativa fictícia.

401 mantém o tratamento de autenticação existente; 4xx (exceto 408) são falhas
conhecidas. 5xx, timeout, conexão, resposta inválida e interrupção são indeterminados.
Interrupção restaura a flag e impede novos envios. Operações preexistentes não são
reenviadas; a Central e seu retry interno permanecem inalterados.

## Consequências e limites

A thread HTTP fica ocupada durante os envios e até 3s de backoff, sem transação
aberta. Os testes substituem a espera por captura dos intervalos solicitados.
Não há atomicidade entre HTTP remoto e banco local: encerramento abrupto do processo
ou indisponibilidade do banco pode impedir auditoria/resultado final. A tentativa
já confirmada no histórico sobrevive ao rollback da persistência do protocolo.
Estado e evento de resultado continuam atômicos: falha de banco nessa gravação pode
preservar o estado anterior. Não há recuperação posterior nesta etapa.
