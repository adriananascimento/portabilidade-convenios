# Plataforma de Portabilidade de Convênios de Desconto em Folha

Aplicação full stack desenvolvida para o desafio técnico, usando **React/TypeScript**, **Java/Spring Boot** e **PostgreSQL**. Permite autorizar beneficiários, consultar parcelas disponíveis, solicitar a portabilidade para outra conta e acompanhar o resultado na **Central de Convênios simulada**.
A Central funciona separadamente, com banco de dados e regras próprias.

## 1. Visão geral e arquitetura

| Componente | Responsabilidade | Tecnologias |
| --- | --- | --- |
| `frontend/` | Interface de autorizações, consulta de agenda, solicitação de portabilidade, listagem, histórico e acompanhamento | React 19, TypeScript 6, Vite 8, Material UI 9 |
| `backend/` | API do Portal, integração com a Central, idempotência, persistência local, auditoria e sincronização de status | Java 21, Spring Boot 4.1.2, Spring Data JPA, RestClient |
| `central-convenios/` | Simulação da Central: autorizações, parcelas, reservas e processamento assíncrono | Java 21, Spring Boot 4.1.2, Spring Data JPA |
| Bancos de dados | Persistência independente do Portal e da Central | PostgreSQL 17, Flyway |
| Infraestrutura local | Compilação e execução dos serviços; entrega estática do frontend | Docker Compose, Nginx |


**Responsabilidades:** o Portal não acessa o banco da Central. A Central mantém os dados oficiais de autorizações, parcelas e resultados. O Portal guarda as solicitações, os status de integração e o histórico de eventos.

### Fluxo funcional

1. Uma autorização é registrada para o beneficiário e seu convênio.
2. O Portal consulta as parcelas que podem ser transferidas.
3. O usuário seleciona as parcelas e informa a conta de destino.
4. O Portal salva a solicitação e a envia à Central com uma chave de idempotência.
5. A Central valida a solicitação, reserva as parcelas e registra a operação como `PENDENTE`.
6. A Central processa a operação depois e a confirma ou rejeita. Falhas técnicas recebem um status próprio.
7. A interface consulta o Portal periodicamente. O Portal consulta a Central quando necessário e mantém o histórico dos eventos.

## 2. Pré-requisitos

Para executar tudo com Docker:

- Docker Engine ou Docker Desktop, com Docker Compose v2.
- Portas locais disponíveis: **3000**, **8080**, **8081**, **5433** e **5434**.
- Acesso às imagens Docker e aos repositórios de dependências durante o build.

Para executar as aplicações na máquina e deixar apenas os bancos no Docker:

- **JDK 21**; os módulos Java incluem Maven Wrapper (`./mvnw`).
- **Node.js 22** e npm.

Execute os comandos a partir da **raiz do projeto**, exceto quando indicado.

## 3. Como executar

### Opção A — Aplicação completa com Docker Compose

A Central e o Portal usam arquivos Docker Compose separados.

**1. Subir a Central simulada e seu PostgreSQL:**

```bash
docker compose -f docker-compose.central.yml up -d --build
```

**2. Subir o Portal, seu PostgreSQL e o frontend:**

```bash
docker compose -f docker-compose.portal.yml up -d --build
```

**3. Verificar os serviços e consultar os logs:**

```bash
docker compose -f docker-compose.central.yml ps
docker compose -f docker-compose.portal.yml ps

docker compose -f docker-compose.central.yml logs -f central
docker compose -f docker-compose.portal.yml logs -f backend frontend
```

| Serviço | Endereço local |
| --- | --- |
| Frontend | http://localhost:3000 |
| API do Portal | http://localhost:8080/api/operacoes |
| Central simulada | http://localhost:8081 |
| PostgreSQL do Portal | `localhost:5433` |
| PostgreSQL da Central | `localhost:5434` |

### Opção B — Aplicações no host e bancos no Docker

**1. Subir apenas os bancos:**

```bash
docker compose -f docker-compose.central.yml up -d --wait postgres
docker compose -f docker-compose.portal.yml up -d --wait postgres
```

**2. Iniciar a Central** (terminal 1):

```bash
cd central-convenios
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/central_convenios ./mvnw spring-boot:run
```

**3. Iniciar o backend do Portal** (terminal 2, a partir da raiz):

```bash
cd backend
./mvnw spring-boot:run
```

**4. Iniciar o frontend** (terminal 3, a partir da raiz):

```bash
cd frontend
npm ci
npm run dev
```

O Vite mostra a URL do frontend no terminal (normalmente `http://localhost:5173`). As chamadas `/api` são encaminhadas para `localhost:8080`.

### Encerrar os ambientes

```bash
docker compose -f docker-compose.portal.yml down
docker compose -f docker-compose.central.yml down
```

`down` para os serviços sem apagar os dados. `down -v` também apaga os volumes e deve ser usado apenas para reiniciar os dados do ambiente.

## 4. Como testar o fluxo

Com os serviços em execução, é possível criar uma autorização, consultar parcelas, solicitar a portabilidade e acompanhar seu status.
A disponibilidade das parcelas depende das autorizações, datas e reservas existentes.

### Dados de teste da Central de Convênios

A Central já possui parcelas fictícias para testar a autorização e a portabilidade.
Use os beneficiários `BEN-001` e `BEN-002` nos testes.

| Beneficiário | Parcela | Convênio | Valor | Data do desconto |
|---|---|---|---|---|
| BEN-001 | PAR-001 | CONV-001 | R$ 350,00 | 05/12/2099 |
| BEN-001 | PAR-002 | CONV-001 | R$ 125,50 | 05/11/2099 |
| BEN-001 | PAR-003 | CONV-002 | R$ 200,00 | 05/10/2099 |
| BEN-001 | PAR-004 | CONV-001 | R$ 90,00 | 05/01/2020 |
| BEN-002 | PAR-005 | CONV-002 | R$ 450,75 | 05/12/2099 |
| BEN-002 | PAR-006 | CONV-002 | R$ 80,00 | 05/02/2020 |

**Como testar:**

1. Crie uma autorização para `BEN-001` ou `BEN-002` pela API da Central.
2. Consulte a agenda de descontos do beneficiário autorizado.
3. Selecione parcelas disponíveis e solicite a portabilidade.
4. Acompanhe a atualização dos status da operação.

**Observações:**
- Os dados são fictícios e servem apenas para testes.
- As parcelas de 2020 servem para testar descontos vencidos.
- As autorizações não vêm cadastradas. Crie uma pela tela de autorizações antes de consultar a agenda e solicitar a portabilidade.

## 5. Simulação e integração com a Central de Convênios

A Central é um **serviço simulado independente**, com API, banco de dados, regras e processamento próprio. Ela não retorna apenas respostas HTTP fixas.

- **Autorização e agenda:** registra autorizações e consulta parcelas disponíveis por beneficiário e convênio.
- **Registro de operação:** valida os dados e a assinatura HMAC-SHA256 do POST, evita registros duplicados e reserva parcelas dentro de uma transação.
- **Concorrência:** usa bloqueios e regras do banco para impedir reservas conflitantes.
- **Processamento assíncrono:** uma tarefa agendada processa operações pendentes e atualiza seus status.
- **Sincronização:** o Portal consulta a Central por HTTP e salva os status. O frontend consulta o Portal periodicamente (polling).
- **Tratamento de falhas:** o envio inicial pode ser tentado até três vezes em falhas temporárias, com intervalos crescentes. O conteúdo, a assinatura e a chave não mudam. Operações com resultado incerto não são reenviadas automaticamente depois.

O HMAC verifica a origem e a integridade do **POST inicial da operação**. Ele não autentica usuários, não substitui HTTPS e não é aplicado a todas as rotas.

## 6. Bibliotecas externas e justificativas

| Biblioteca ou ferramenta | Onde | Justificativa |
| --- | --- | --- |
| Spring Boot / Spring Web MVC | Portal e Central | APIs REST, injeção de dependências e estrutura consistente de aplicação |
| Spring Data JPA / Hibernate | Portal e Central | Persistência relacional, transações e controle de concorrência |
| Spring Validation | Portal e Central | Validação declarativa de entradas e contratos |
| Flyway + módulo PostgreSQL | Portal e Central | Evolução versionada e reproduzível dos esquemas |
| PostgreSQL JDBC Driver | Portal e Central | Comunicação com PostgreSQL |
| Spring RestClient | Portal | Integração HTTP tipada com a Central e configuração de timeouts |
| Spring Boot Actuator | Portal e Central | Infraestrutura para endpoints de saúde/monitoramento; observabilidade de negócio ainda não instrumentada |
| Testcontainers + JUnit Jupiter | Central, testes | PostgreSQL isolado para validar persistência, migrations e concorrência reais |
| React + React DOM | Frontend | Componentização e gerenciamento de estado da interface |
| React Router | Frontend | Navegação entre telas e rotas da SPA |
| Material UI / Emotion | Frontend | Componentes visuais, formulários, mensagens e tema consistentes |
| MUI X Data Grid | Frontend | Tabelas com paginação e recursos de apresentação de dados |
| TypeScript | Frontend | Tipagem e validação estática dos contratos utilizados na interface |
| Vite | Frontend | Servidor de desenvolvimento, proxy e build de produção |
| Testing Library + jsdom | Frontend, testes | Testes de componentes em ambiente DOM simulado |
| Docker Compose / Nginx | Execução local | Orquestração dos serviços e entrega da SPA com proxy `/api` |

O HMAC-SHA256 usa recursos da própria JDK, **sem biblioteca criptográfica extra**. As novas tentativas HTTP também foram implementadas sem biblioteca específica.

## 7. Testes e validação

**Frontend:**

```bash
npm ci --prefix frontend
npm test --prefix frontend
npm run lint --prefix frontend
npm run build --prefix frontend
```

**Backend do Portal:**

```bash
cd backend
./mvnw test
```

**Central simulada:**

```bash
cd central-convenios
./mvnw test
```

## 8. Decisões arquiteturais

As decisões técnicas estão em [`docs/adr/`](docs/adr/):

| ADR | Decisão |
| --- | --- |
| 0001 | Isolamento da Central de Convênios e dos ambientes |
| 0002 | Processamento assíncrono e reserva de parcelas |
| 0003 | Retry interno do processamento da Central |
| 0004 | Polling no frontend para acompanhamento |
| 0005 | Persistência local, idempotência e listagem de operações no Portal |
| 0006 | Auditoria persistente de operações |
| 0007 | Sincronização manual de status e controle de concorrência |
| 0008 | Retry limitado do envio inicial à Central |
| 0009 | Assinatura HMAC-SHA256 no envio inicial de operações |

### Trade-offs considerados

| Escolha | Benefício | Limitação assumida |
| --- | --- | --- |
| Central independente, com banco próprio | Simula fronteira real de integração e evita acoplamento de dados | Dois serviços e dois bancos para configurar e manter |
| HTTP síncrono para registro/consulta | Simplicidade de implementação e depuração | Falhas e latência de rede precisam de tratamento explícito |
| Processamento assíncrono na Central | Separa aceite da operação de seu resultado | Necessidade de acompanhar estados intermediários |
| Polling em vez de WebSocket/SSE | Menor complexidade de infraestrutura para o escopo do case | Requisições periódicas e atualização não instantânea |
| Persistência local e auditoria | Rastreabilidade, idempotência e consulta mesmo quando a Central falha | Possibilidade de divergência temporária entre serviços |
| Transações curtas + controle otimista | Evita manter transações abertas durante HTTP | Exige tratamento de conflitos e revalidação de estados |
| Retry limitado no envio inicial | Recupera algumas falhas transitórias sem nova dependência | Não resolve automaticamente operações que ficaram indeterminadas após a requisição |
| HMAC no POST de operação | Verifica integridade/autenticidade da mensagem entre serviços | Não substitui TLS, autenticação de usuário ou gestão de segredos de produção |

O Portal e a Central não compartilham uma transação. Se houver timeout ou queda de conexão, o Portal pode não saber o resultado da operação na Central. Esse caso é registrado com um status específico.

## 9. Documentação de IA e harness do agente

O **Codex** foi usado como apoio no desenvolvimento. A documentação mostra **as solicitações, orientações, decisões técnicas e resultados dos testes**.

| Documento | Conteúdo |
| --- | --- |
| [`AGENTS.md`](AGENTS.md) | Instruções do agente: contexto, padrões, idioma, limites de escopo e fluxo de trabalho |
| [`docs/adr/`](docs/adr/) | Decisões arquiteturais e alternativas consideradas |
| [`docs/ia/backend.md`](docs/ia/backend.md) | Solicitações, orientações, decisões, implementações e validações do Portal |
| [`docs/ia/central-convenios.md`](docs/ia/central-convenios.md) | Histórico de IA da Central, incluindo registros iniciais e validações posteriores |
| [`docs/ia/frontend.md`](docs/ia/frontend.md) | Histórico de IA da interface, decisões de UX/integração e testes |

**Como o harness foi usado:** o arquivo `AGENTS.md` e o contexto de cada tarefa orientaram o Codex. As propostas foram avaliadas e ajustadas quando necessário, e as implementações passaram por testes e correções. Os arquivos em `docs/ia/` trazem **resumos dos prompts, restrições e resultados**, não conversas completas.
Os registros mostram as decisões aprovadas, o apoio da IA e as revisões finais que foram documentadas.

## 10. Limitações e evolução com mais tempo

As melhorias abaixo **ainda não foram implementadas**:

1. **Cadastro de beneficiários e contas bancárias:** cadastrar beneficiários e suas contas para selecionar os dados na portabilidade, sem preenchê-los toda vez.
2. **Atualização de status por eventos:** usar RabbitMQ ou Amazon SQS para enviar mudanças de status da Central ao Portal. Usar SSE ou WebSocket para atualizar o frontend em tempo real, sem polling.
3. **Recuperação de operações pendentes:** consultar e recuperar automaticamente operações sem confirmação da Central.
4. **Tratamento de falhas técnicas:** permitir reprocessar ou tratar manualmente operações com `FALHA_TECNICA`, liberando parcelas reservadas com segurança quando necessário.
5. **Testes ponta a ponta:** testar automaticamente todo o fluxo, da autorização até o resultado da portabilidade, incluindo a comunicação entre serviços.
6. **Segurança:** adicionar login e permissões, HTTPS, proteção de dados e gestão segura das chaves de integração.
7. **Logs e alertas:** centralizar logs e criar alertas para falhas, operações pendentes, reenvios e lentidão.
8. **Automação de testes e deploy:** criar pipelines CI/CD para testar, verificar dependências e publicar as aplicações.
9. **Performance e escalabilidade:** melhorar consultas, paginar o histórico e preparar a comunicação entre serviços para mais usuários e operações.
10. **Monitoramento com Prometheus e Grafana:** coletar métricas de requisições, erros, tempo de resposta e uso de recursos com Prometheus. Criar dashboards no Grafana para acompanhar a saúde dos serviços.

## 11. Estrutura do repositório

```text
.
├── backend/                  # API e persistência do Portal
├── central-convenios/        # Central simulada e processamento
├── frontend/                 # Layout React/TypeScript
├── docs/
│   ├── adr/                  # Decisões arquiteturais
│   └── ia/                   # Registros do uso de IA
├── AGENTS.md                 # Orientações do agente
├── docker-compose.central.yml
├── docker-compose.portal.yml
└── README.md
```

---

**Escopo da entrega:** aplicação de demonstração com integração, portabilidade, persistência, auditoria e acompanhamento de status. A Central e os dados são simulados. Não há transações bancárias reais.
