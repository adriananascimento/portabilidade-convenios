# Portabilidade de Convênios

## Contexto do projeto

Este repositório contém a solução de um desafio técnico para implementação de uma plataforma de portabilidade de convênios de desconto em folha.

O objetivo é construir uma aplicação funcional, segura, testável e de fácil execução, priorizando clareza de código e decisões técnicas justificáveis.

## Tecnologias

- Backend: Java 21, Spring Boot 4.1.2, Maven.
- Frontend: React com TypeScript.
- Banco de dados: PostgreSQL.
- Migrações: Flyway.
- Infraestrutura local: Docker Compose.
- Testes: JUnit e ferramentas de teste apropriadas ao projeto.

## Idioma e nomenclatura

- Utilizar português nos nomes de classes de domínio, atributos de negócio, endpoints, mensagens e documentação.
- Preservar nomes técnicos convencionais de frameworks, bibliotecas e padrões quando apropriado, como Controller, Service, Repository e DTO.
- Manter consistência com a nomenclatura existente.
- Não traduzir nomes de bibliotecas ou APIs externas.

## Princípios de desenvolvimento

- Priorizar soluções simples, legíveis e adequadas ao escopo do desafio.
- Evitar abstrações prematuras e complexidade desnecessária.
- Separar responsabilidades e respeitar os limites dos módulos.
- Tratar erros de forma explícita.
- Considerar segurança, idempotência, concorrência e rastreabilidade quando relevantes.
- Não adicionar dependências sem justificativa.
- Não modificar funcionalidades não relacionadas à tarefa solicitada.

## Orientações para agentes de IA

- Executar somente o escopo solicitado.
- Não tomar decisões arquiteturais relevantes sem apresentá-las para aprovação.
- Não implementar funcionalidades adicionais por iniciativa própria.
- Reutilizar padrões e convenções existentes.
- Explicar brevemente alterações realizadas e suas motivações.
- Informar comandos de validação e resultados obtidos.
- Não afirmar que testes foram executados quando não foram.
- Não incluir segredos, credenciais reais ou informações sensíveis no código.
- Solicitar esclarecimentos quando houver ambiguidades que impactem o comportamento do sistema.

## Qualidade e testes

- Criar testes automatizados para regras de negócio e cenários críticos.
- Priorizar testes de idempotência, transições de estado e falhas de integração.
- Não remover testes para corrigir falhas.
- Evitar mocks excessivos que escondam problemas reais.
- Garantir que alterações existentes não sejam prejudicadas.

## Documentação

- Manter o README atualizado com instruções de execução.
- Registrar decisões arquiteturais relevantes em `docs/adr/`.
- Documentar o uso efetivo de IA em `docs/ia/ia-doc.md`, incluindo tarefas, orientações relevantes, resultados e revisões humanas.
- Não inventar prompts, resultados de testes ou decisões anteriores.

## Fluxo de trabalho

1. Compreender a tarefa e identificar os arquivos envolvidos.
2. Propor a implementação quando houver decisões relevantes.
3. Realizar apenas as alterações autorizadas.
4. Executar as validações disponíveis.
5. Resumir alterações, testes e eventuais limitações.
6. Aguardar orientação antes de iniciar outra funcionalidade.
