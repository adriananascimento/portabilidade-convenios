# ADR-0001 — Isolamento da Central de Convênios

## Contexto

O desafio técnico exige a integração com uma Central de Convênios externa, responsável pelo registro de autorizações, consulta da agenda de descontos e processamento das operações de portabilidade.
Como não existe uma Central real disponível, foi necessário implementar uma simulação que reproduzisse seu comportamento, incluindo o processamento assíncrono e possíveis falhas de comunicação.
Para representar melhor esse cenário, a Central precisava funcionar de forma independente do sistema principal.

## Decisão

A Central de Convênios foi implementada como uma aplicação Spring Boot independente do backend principal (Portal).
Cada aplicação possui:
- Projeto Java e configuração Maven próprios.
- Banco de dados PostgreSQL independente.
- Migrações Flyway próprias.
- Arquivo `application.yaml` específico.
- Configurações de ambiente e portas distintas.
A comunicação entre o Portal e a Central é realizada por HTTP, sem acesso direto ao banco de dados da outra aplicação.
Para execução com Docker, foram criados dois arquivos na raiz do projeto:
- `docker-compose.central.yml`
- `docker-compose.portal.yml`

## Justificativa

A separação permite simular uma integração mais próxima de um sistema externo real, principalmente em situações de indisponibilidade, timeout ou falhas de comunicação.
Permite iniciar, interromper e configurar os ambientes de forma independente, além de evitar que as duas aplicações compartilhem diretamente seus bancos de dados.
Também evita o compartilhamento de dados e responsabilidades entre as aplicações, permitindo que cada uma tenha suas próprias regras e persistência.
Além disso, facilita testar os mecanismos de idempotência, retry e tratamento de erros necessários para a integração.
A comunicação entre o Portal e a Central continua sendo realizada exclusivamente por HTTP, respeitando os contratos das APIs.
Essa organização também facilita os testes de integração e a simulação de indisponibilidade da Central, sem precisar interromper o Portal.

## Alternativas consideradas

**Central integrada ao backend principal:** seria mais simples de implementar e executar, mas não representaria tão bem a comunicação entre sistemas independentes.
**Central independente com banco compartilhado:** reduziria a infraestrutura necessária, mas criaria dependência entre os modelos de dados das aplicações.
**Docker Compose único:** também seria uma opção válida. A separação em dois arquivos foi escolhida para permitir iniciar e executar os ambientes de forma independente.

## Consequências

**Positivas:**
- Separação das responsabilidades e dos dados de cada aplicação.
- Possibilidade de simular falhas reais de integração.
- Maior independência para manutenção e evolução dos serviços.
- Testes de integração mais próximos do cenário proposto no desafio.

**Negativas:**
- Necessidade de configurar e executar duas aplicações e dois bancos de dados.
- Maior complexidade na configuração do ambiente.
- Necessidade de tratar falhas de comunicação e possíveis inconsistências entre os sistemas.

Como as aplicações possuem bancos independentes, não existe uma transação única envolvendo o Portal e a Central. Por isso, foram implementados mecanismos de idempotência, retry e sincronização para lidar com parte desses cenários.
