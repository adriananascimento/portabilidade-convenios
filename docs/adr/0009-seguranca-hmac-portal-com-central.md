# ADR-0009 — Segurança da integração com a Central de Convênios

## Contexto

O Portal realiza requisições HTTP à Central de Convênios para registrar operações de portabilidade.
Como essas requisições envolvem informações utilizadas no processamento das operações, é necessário garantir que o conteúdo enviado não seja alterado durante a comunicação e que a Central consiga verificar sua autenticidade.

## Decisão

Utilizar **HMAC-SHA256** para assinar as requisições de registro de operações enviadas pelo Portal à Central de Convênios.
O Portal gera uma assinatura a partir do conteúdo da requisição e de uma chave secreta compartilhada entre as duas aplicações.
Ao receber a solicitação, a Central calcula a assinatura utilizando a mesma chave e verifica se ela corresponde à assinatura recebida.
Caso a validação falhe, a solicitação é rejeitada antes do registro da operação.
A assinatura é aplicada somente ao endpoint de criação da operação. As consultas de status não utilizam esse mecanismo.

### Integração com a idempotência e o retry

O conteúdo da requisição e sua assinatura são gerados antes da primeira tentativa de envio.
Caso ocorra uma falha transitória, as novas tentativas reutilizam o mesmo conteúdo, a mesma chave de idempotência e a mesma assinatura HMAC.
Isso mantém a consistência entre as tentativas e permite que a Central identifique requisições repetidas.
A chave secreta deve ser configurada nas aplicações sem ficar armazenada diretamente no código-fonte.

## Justificativa

O HMAC-SHA256 permite verificar a autenticidade e a integridade do conteúdo enviado, utilizando uma chave compartilhada entre os sistemas.
É uma solução relativamente simples de implementar e adequada ao cenário de integração entre o Portal e a Central.
A assinatura também pode ser validada sem necessidade de consultar um serviço externo de autenticação.

## Alternativas consideradas

**Não utilizar assinatura:** simplificaria a integração, mas não permitiria verificar a autenticidade e a integridade do conteúdo no nível da aplicação.
**Utilizar autenticação com OAuth2:** permitiria um controle de autorização mais abrangente, mas exigiria uma estrutura adicional de emissão e validação de tokens, aumentando a complexidade do projeto.
**Utilizar certificados digitais para autenticação mútua (mTLS):** permitiria autenticar os sistemas durante o estabelecimento da conexão, mas exigiria gerenciamento de certificados e configuração adicional da infraestrutura.

## Consequências

**Positivas:**

- Verificação da autenticidade das requisições de registro.
- Detecção de alterações no conteúdo assinado.
- Implementação sem dependência de um serviço externo de autenticação.
- Reutilização da assinatura durante as tentativas de envio.

**Negativas:**

- Necessidade de configurar e proteger a chave secreta nas duas aplicações.
- Alterações na representação do conteúdo assinado podem invalidar a assinatura.
- A proteção não se aplica aos endpoints de consulta de status.

O HMAC não criptografa o conteúdo da requisição e não substitui o HTTPS, que continua sendo necessário para proteger os dados durante a comunicação.
Além disso, o HMAC isoladamente não impede a repetição de uma requisição válida. O controle de duplicidade é realizado pelo mecanismo de idempotência da integração.
