# 0002 — Processamento e reserva de parcelas

Status: aprovada pelo usuário nesta sessão; implementada.

## Contexto

A unicidade global de parcela em operacoes_parcelas impede nova tentativa após
rejeição. Remover vínculos perderia o histórico e quebraria a comparação de
conteúdo para idempotência. A V5 existente já permite os estados finais.

## Decisão

Manter @ElementCollection e operacoes_parcelas como histórico imutável de IDs.
Substituir a unicidade global por uma tabela de reservas ativas, com parcela
como chave única e referência ao vínculo histórico da operação. Preencher a
reserva a partir dos vínculos existentes de operações pendentes/confirmadas.
Criar e liberar reservas na mesma transação que altera a operação; confirmação
mantém a reserva e rejeição a libera. A agenda deve excluir reservas ativas.

Executar um serviço de processamento diretamente invocável e um agendador
@Scheduled com intervalo e atraso mínimo configuráveis. Cada operação será
bloqueada pessimisticamente e revalidada dentro de transação própria, incluindo
parcelas bloqueadas em ordem de ID. Revalidar beneficiário, status disponível,
data de desconto >= hoje UTC e autorização ativa por convênio. Confirmar se
válida; caso contrário rejeitar com motivo. Operações finalizadas não sofrem
novas transições. POST e GET não chamam o processador.

## Consequências

Preserva o contrato do POST, histórico e idempotência após finalização, sem
converter a coleção de IDs em entidade. Exige migração incremental das reservas
e testes reais de concorrência, rollback e integridade. V7 migra reservas
existentes e aplica PK por parcela e FK composta para o vínculo histórico.
O estado DISPONIVEL de parcelas não é duplicado por estados de reserva: a
consulta da agenda deriva a disponibilidade da ausência de reserva ativa.

O processamento seleciona lotes de até 100, bloqueia cada operação antes de
reavaliar estado/idade e usa REQUIRES_NEW por operação. Leitura de autorizações
usa lock compartilhado para impedir revogação durante a confirmação. Erros
técnicos deixam a operação pendente por rollback e são registrados; não são
tratados como rejeição de negócio. O agendador pode ser desabilitado sem impedir
chamadas diretas ao serviço de processamento nos testes.
