# Uso de IA — Central de Convênios

O desenvolvimento foi realizado de forma incremental, com apoio de IA nas atividades documentadas de análise, implementação, testes e documentação.
Os requisitos e restrições, as propostas e aprovações técnicas foram feitas manualmente. O uso de IA apoiou a execução das atividades.

## Orientações e restrições

- Preservar a separação entre Central, backend do Portal e frontend.
- Manter o domínio em português e a organização por camadas prevista no `AGENTS.md`.
- Respeitar os contratos existentes e limitar alterações ao escopo de cada tarefa.
- Evitar dependências e funcionalidades não solicitadas.
- Submeter decisões arquiteturais relevantes à avaliação antes de implementá-las.
- Registrar testes executados, resultados, limitações e alterações preexistentes preservadas.

## Atividades e validações

Os registros a seguir apresentam solicitações, implementações auxiliadas, decisões documentadas e evidências de validação. As contagens de testes correspondem a execuções históricas, não necessariamente ao estado atual da suíte. Etapas intermediárias foram preservadas, inclusive propostas aprovadas posteriormente.

## Listagem paginada de autorizações

- Tarefa: implementar GET /autorizacoes paginado, mantendo o POST existente.
- Trabalho do Codex: inspeção dos padrões; adição de AutorizacoesPaginaDTO,
  método de consulta no service e endpoint no controller; reutilização de
  JpaRepository.findAll(Pageable), sem alteração do repository.
- Decisões: paginação no banco, tamanho de 1 a 100 (padrão 10), página a partir
  de zero; data e UUID decrescentes; transação somente leitura e DTO explícito.
  A entidade não tem associações carregadas pela listagem, evitando N+1.
- Testes adicionados: AutorizacaoListagemIntegracaoTest, com HTTP e PostgreSQL
  Testcontainers; cobre ordem, desempate, páginas distintas, metadados, campos
  de resposta, padrões, página vazia e parâmetros inválidos.
- Validação: ./mvnw test — 43 testes, zero falhas/erros/ignorados, BUILD SUCCESS.
- Orientações: escopo limitado às autorizações, português, camadas existentes,
  sem bibliotecas novas e sem alterar operações de portabilidade. Alterações locais
  preexistentes foram preservadas. README atualizado com uso e contrato JSON.
- Execução: primeira tentativa Maven foi bloqueada pelo sandbox no cache .m2;
  repetição com permissão de execução ampliada concluiu os testes. Maven emitiu
  avisos HTTP 401 para metadados de repositórios externos, sem impedir o build.

## Falha de autenticação na integração

- Ferramenta: Codex. Orientação: alterações pontuais, preservar HMAC, transações,
  auditoria e idempotência; não expor dados sensíveis nem adicionar reenvio.
- Nenhuma alteração no código de produção ou na validação HMAC.
- Teste HTTP adicionado para assinatura inválida: espera 401 e nenhuma operação
  persistida. Helper dos testes de operações passa a assinar requisições válidas
  com a configuração de teste existente, para preservar cobertura das regras.
- Revisão humana final ainda não registrada.
- Validação: `cd central-convenios && ./mvnw -o -q test`: 44 testes, 2 falhas, zero erros ou ignorados; código de saída 1.
  O teste novo de assinatura inválida passou. Falhas nos testes existentes:
  deveCriarOperacaoComVinculosEConta espera 2 campos, mas o DTO atual tem 4;
  deveValidarListaCamposEHeader espera 400, mas ConstraintViolationException
  lançada pelo controller atual não possui handler e resulta em 500.
  Nenhuma correção fora do escopo foi aplicada.
- Tentativa inicial bloqueada pelos locks do cache Maven no sandbox; execução
  repetida com permissão ampliada. `git diff --check` passou.

## Verificação durante retry do backend

- Codex executou `cd central-convenios && ./mvnw -o -q test` sem alterar código
  ou retry interno da Central. Resultado: 44 testes, 2 falhas, zero erros/ignorados.
- `OperacaoIntegracaoTest.deveCriarOperacaoComVinculosEConta`: espera 2 campos JSON,
  recebe 4. `deveValidarListaCamposEHeader`: espera HTTP 400, recebe 500 na linha 139.
  Falhas fora do código modificado.

## Finalização do frontend

- Trabalho do Codex restrito ao frontend e documentação; Central não alterada.
- Interface distingue falha entre serviços (502) de autenticação/permissão do
  usuário (401/403), respeitando o contrato do Portal.
- Testes da Central não executados nesta tarefa; testes com serviços reais não
  realizados. Resultados do frontend em [frontend.md](frontend.md).
