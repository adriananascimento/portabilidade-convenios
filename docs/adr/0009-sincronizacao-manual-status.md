# ADR-0009 — Sincronização manual do status oficial

## Contexto e decisão

Proposta aprovada previamente pelo usuário: endpoint POST, resposta com
OperacaoListagemDTO, correção dos timeouts e concorrência otimista com @Version.
A Central possui GET `/operacoes-portabilidade/{protocolo}`. A leitura de
ProcessamentoOperacaoService e OperacaoPortabilidade da Central confirmou:

- PENDENTE pode permanecer PENDENTE ou mudar para CONFIRMADA, REJEITADA ou FALHA_TECNICA.
- CONFIRMADA e REJEITADA não têm saída.
- FALHA_TECNICA não tem saída implementada: o processamento busca só PENDENTES.
  Ela continua elegível para consultas, conforme solicitado; resposta diferente
  é tratada como inconsistente, sem modificar o estado local.

A ADR-0004 descreve uma evolução com polling no frontend. Esta etapa implementa
somente acionamento manual no backend, sem polling, retry ou agendamento.

## Fluxo e contrato

POST `/api/operacoes/{id}/sincronizar-status`, sem corpo, usa UUID local.
Uma transação curta lê operação e versão. Operação inexistente retorna 404.
CONFIRMADA e REJEITADA retornam estado local com 200, sem HTTP externo. Ausência
de protocolo retorna 409, sem consulta nem status inventado.

Consultar a Central fora de transação; validar corpo, protocolo e status.
Status desconhecido no JSON ou ausente não recebe valor padrão. Para status
local nulo com protocolo, resposta válida estabelece o primeiro status oficial
conhecido, sem presumir uma transição anterior da Central.

Em outra transação, comparar versão e status. Se igual, não gravar nada. Uma
transição permitida atualiza somente statusOperacao e acrescenta
STATUS_OPERACAO_ATUALIZADO com status anterior/novo, na mesma transação.
statusIntegracao não muda. Falha de persistência reverte estado e evento; falha
de consulta não vira FALHA_ENVIO nem rejeição de negócio.

Resposta 200 reutiliza OperacaoListagemDTO: id, beneficiarioId, dataCriacao local,
statusIntegracao, statusOperacao e protocoloCentral. Não expor a versão técnica.

## Concorrência

Migration V4 acrescenta versao BIGINT NOT NULL DEFAULT 0; @Version protege
atualizações JPA, incluindo o fluxo existente de criação. HTTP não segura locks
ou transações de banco. A gravação compara a versão inicial; UPDATE versionado
também detecta alterações até flush/commit. A transação perdedora reverte o evento.

Após conflito, ler novamente em outra transação: retornar estado atual se for
CONFIRMADA/REJEITADA ou igual ao recebido. Caso contrário, 409, sem aplicar a
resposta antiga. Não repetir HTTP nem escrita. Consultas simultâneas podem
ocorrer; apenas uma grava a mesma transição. Respostas finais divergentes não
substituem o estado final já persistido pela sincronização concorrente.

## Falhas e limites

- Timeout local ou HTTP 408/504: 504.
- Conexão recusada, DNS ou HTTP 5xx (exceto 504): 503.
- Protocolo remoto inexistente: 502 com descrição específica; 404 é local.
- Status inválido, corpo inesperado, protocolo divergente, transição incompatível
  ou demais HTTP inesperados: 502.
- Falha na persistência da atualização: 500 com mensagem de falha local.

Erros usam ProblemDetail sem detalhes internos. Preservar o último estado
confirmado. Não adicionar eventos para consultas sem transição ou que falharam.
A resposta representa o estado persistido na leitura/transação final, sem garantia
que permanecerá atual após responder. Não há versão temporal remota; versões
locais impedem aplicar observações após alterações locais detectadas.

RestClientConfig passa a usar o SimpleClientHttpRequestFactory configurado:
conexão 3 segundos e leitura 5 segundos. A configuração vale para todos os métodos
do cliente existente. Testes verificam ambos os valores ligados ao builder e
timeout real de leitura em servidor local. Timeout de conexão é validado por
configuração: uma rede externa silenciosa não é determinística em teste.
