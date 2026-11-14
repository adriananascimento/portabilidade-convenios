# ADR-0007 — Listagem local de operações

## Decisão aprovada

O GET `/api/operacoes` consulta `OperacaoPortabilidadeRepository` com `Pageable`
e ordenação `dataCriacao DESC, id DESC`. A paginação e suas validações permanecem.
Nenhuma chamada à Central é realizada; os estados representam o registro local,
sem atualização remota durante a leitura.

A listagem tem DTO próprio com `id`, `beneficiarioId`, `dataCriacao` local,
`statusIntegracao`, `statusOperacao` e `protocoloCentral`. Status oficial e protocolo
podem ser nulos. O usuário aprovou explicitamente remover os aliases `protocolo`
e `status`. O DTO da criação e seu fluxo não mudam.

## Consequências

Operações ainda não integradas passam a aparecer. A troca do contrato é
incompatível com a tela atual: o frontend deverá separar os tipos de GET e POST,
usar `id` na identificação das linhas, apresentar o protocolo apenas como
referência opcional e tratar ambos os status e valores nulos. A data passa a ser
local. A adaptação do frontend foi adiada explicitamente pelo usuário.
Não são necessários novos campos no banco, dependências ou mudanças na Central.
