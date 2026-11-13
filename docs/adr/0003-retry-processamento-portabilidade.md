# ADR-0003 — Retry no processamento de portabilidade

Status: implementada conforme requisitos do usuário;

## Contexto e decisão

O processador repetia qualquer falha técnica em cada ciclo, sem limite nem espera.
Preservar @Scheduled, locks pessimistas e TransactionTemplate REQUIRES_NEW.
V9 é a próxima migração após V8; nenhuma migração anterior é alterada.

Persistir tentativas_processamento (0–5), proxima_tentativa_em e ultimo_erro.
Cada conclusão, inclusive rejeição de negócio, conta uma tentativa; em falha,
o incremento da transação original sofre rollback e o registro independente o
repõe. Registros anteriores à V9 começam em zero, sem inventar histórico.
Os intervalos após falhas transitórias são 5, 10, 20 e 40 segundos, calculados
com Clock a partir do registro da falha, sem bloquear threads. Consulta de lote
e revalidação sob lock verificam idade mínima e próximo horário.

Classificar como transitórias TransientDataAccessException e SQLSTATE de conexão
(classe 08), serialização (40001), deadlock (40P01) e lock indisponível (55P03),
inclusive nas causas de exceções de commit. Outras falhas técnicas são permanentes
ou não classificadas e encerram imediatamente em FALHA_TECNICA. Não presumir que
qualquer RuntimeException seja recuperável. Rejeições de negócio continuam
REJEITADA e liberam reservas atomicamente; não entram no retry técnico.

Após execute concluir o rollback, registrar falha em nova transação. Bloquear
novamente a operação e conferir PENDENTE e o contador capturado após o lock da
tentativa original. Assim, sucesso ou registro concorrente invalidam o registro
tardio. A quinta falha transitória encerra em FALHA_TECNICA, com data de
processamento, sem próximo horário e sem motivo de rejeição de negócio.
Nunca liberar reservas em falha técnica; preservar vínculos e último erro para
análise/reconciliação. Não há endpoint novo de reconciliação automática.

## Trade-offs e limites

- Mensagens persistidas são categorias fixas, sem mensagem original, SQL,
  credenciais, conta ou dados de beneficiário; perde-se detalhe diagnóstico.
  O log do processador inclui protocolo, sem stack trace da falha técnica.
- ultimo_erro preserva a última falha mesmo após sucesso; contador registra
  tentativas e vínculos preservam histórico, sem criar diário de cada falha.
- Entre rollback e registro existe uma janela em que outra execução pode iniciar.
  O lock e o contador impedem sobrescrita ou contagem duplicada da mesma geração,
  mas não garantem contabilizar cada execução física concorrente que falhou.
- Queda do processo ou indisponibilidade do banco durante o registro pode impedir
  persistir a tentativa. Falhas anteriores ao lock/revalidação não contam como
  processamento iniciado. Garantia estrita nessa janela exigiria outro protocolo
  de claim/lease persistido, fora do escopo de preservar o fluxo atual.
- Se a confirmação de commit for perdida na conexão, revalidar o estado persistido
  protege a conclusão bem-sucedida antes de registrar falha.
- Os horários são mínimos: a execução depende do próximo ciclo e da fila de 100.
  Sem jitter, adequado ao escopo local; períodos são fixos conforme solicitado.
- Contratos dos DTOs permanecem; a consulta expõe o novo status, mas os campos
  operacionais de retry ficam persistidos para diagnóstico interno.

## Verificação

Testes com PostgreSQL/Testcontainers, falhas SQL reais adiadas até o commit,
Clock fixo, barreiras/latches para concorrência e doubles com delegação aos repositories reais, limitados às falhas de
programação e à janela entre rollback e registro. Sem Thread.sleep.
