# ADR-0001 — Isolamento da Central de Convênios

## Contexto

O desafio técnico exige a integração com uma Central de Convênios externa, responsável pelo registro de autorizações, consulta de parcelas e processamento de solicitações de portabilidade.

Como não existe uma Central real disponível para integração, será desenvolvido um serviço simulado que reproduza seu contrato e comportamento.

É necessário garantir que essa simulação represente adequadamente a comunicação entre sistemas independentes, permitindo também testar cenários de falha e indisponibilidade.

## Decisão

A Central de Convênios será implementada como uma aplicação independente do backend de portabilidade.

A aplicação terá:

- Projeto Spring Boot e configuração Maven próprios.
- Banco de dados PostgreSQL independente.
- Migrações Flyway próprias.
- Arquivo `application.yml` próprio.
- Arquivo `docker-compose.yml` próprio.
- Configurações de ambiente e portas distintas.
- Ciclo de inicialização e execução independente.

## Justificativa

A separação foi escolhida para:

- Simular de maneira mais realista a integração com um sistema externo.
- Garantir isolamento entre dados e responsabilidades.
- Permitir testar indisponibilidade, timeout e falhas de comunicação.
- Evitar acoplamento entre as implementações.
- Possibilitar a execução e manutenção independente de cada aplicação.

## Consequências

**Positivas:**

- Maior fidelidade à arquitetura de integração proposta no desafio.
- Melhor isolamento de responsabilidades e persistência.
- Facilidade para simular falhas e validar mecanismos de resiliência.
- Possibilidade de substituir a Central simulada por uma integração real sem alterar as regras de negócio principais.

**Negativas:**

- Necessidade de configurar e executar duas aplicações e dois bancos de dados.
- Maior quantidade de arquivos de infraestrutura e configurações.
- Necessidade de tratar comunicação de rede e falhas entre os serviços.

## Alternativas consideradas

**Central integrada ao backend principal:** descartada por não representar adequadamente a independência de um sistema externo.

**Central independente com banco de dados compartilhado:** descartada por permitir acoplamento pela camada de persistência.

**Docker Compose único para os dois ambientes:** alternativa tecnicamente válida, mas não escolhida para manter a configuração e a execução de cada aplicação organizadas separadamente.