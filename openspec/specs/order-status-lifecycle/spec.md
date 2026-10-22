# order-status-lifecycle Specification

## Purpose
Define os estados de um pedido de delivery, as transições permitidas entre eles e o histórico de mudanças que permite rastrear cada pedido do recebimento à entrega ou cancelamento.

## Requirements

### Requirement: Estados do pedido
Um pedido MUST estar sempre em exatamente um dos estados `RECEBIDO`, `EM_PREPARO`, `SAIU_PARA_ENTREGA`, `ENTREGUE` ou `CANCELADO`. Todo pedido novo MUST começar em `RECEBIDO`. `ENTREGUE` e `CANCELADO` são estados finais.

#### Scenario: Estado inicial
- **WHEN** um pedido é criado
- **THEN** seu status é `RECEBIDO`

### Requirement: Transições de status permitidas
O sistema SHALL aceitar somente as transições: `RECEBIDO→EM_PREPARO`, `RECEBIDO→CANCELADO`, `EM_PREPARO→SAIU_PARA_ENTREGA`, `EM_PREPARO→CANCELADO` e `SAIU_PARA_ENTREGA→ENTREGUE`. Qualquer outra transição, inclusive para o mesmo estado ou a partir de estado final, MUST ser rejeitada com `409 Conflict` sem alterar o pedido.

#### Scenario: Fluxo completo de entrega
- **WHEN** o status de um pedido é alterado sucessivamente para `EM_PREPARO`, `SAIU_PARA_ENTREGA` e `ENTREGUE`
- **THEN** cada requisição responde `200` e o status final é `ENTREGUE`

#### Scenario: Cancelamento antes da saída
- **WHEN** um pedido em `RECEBIDO` ou `EM_PREPARO` é alterado para `CANCELADO`
- **THEN** o sistema responde `200` e o status passa a ser `CANCELADO`

#### Scenario: Cancelamento após a saída
- **WHEN** um pedido em `SAIU_PARA_ENTREGA` é alterado para `CANCELADO`
- **THEN** o sistema responde `409` com `code` `INVALID_STATUS_TRANSITION` e o status permanece `SAIU_PARA_ENTREGA`

#### Scenario: Resposta de conflito explica a regra
- **WHEN** qualquer transição é rejeitada com `409`
- **THEN** o corpo informa `currentStatus`, `requestedStatus` e `allowedTransitions`, e o `detail` descreve a transição recusada (ex.: "Não é possível ir de SAIU_PARA_ENTREGA para CANCELADO")

#### Scenario: Regras publicadas na documentação da API
- **WHEN** alguém consulta a documentação OpenAPI de `PATCH /api/v1/orders/{id}/status`
- **THEN** a descrição lista as transições permitidas e o significado das respostas `400` e `409`

#### Scenario: Pular etapa
- **WHEN** um pedido em `RECEBIDO` é alterado diretamente para `ENTREGUE`
- **THEN** o sistema responde `409` com `code` `INVALID_STATUS_TRANSITION`

#### Scenario: Voltar etapa
- **WHEN** um pedido em `SAIU_PARA_ENTREGA` é alterado para `EM_PREPARO`
- **THEN** o sistema responde `409`

#### Scenario: Estado final é imutável
- **WHEN** um pedido em `ENTREGUE` ou `CANCELADO` recebe qualquer novo status
- **THEN** o sistema responde `409` e o pedido não é alterado

#### Scenario: Mesmo estado
- **WHEN** um pedido em `EM_PREPARO` é alterado para `EM_PREPARO`
- **THEN** o sistema responde `409`

### Requirement: Atualização de status via API
O sistema SHALL expor `PATCH /api/v1/orders/{id}/status` com corpo `{ "status": "<ESTADO>" }` para usuários autenticados, retornando o pedido atualizado. Pedido inexistente MUST resultar em `404`.

#### Scenario: Atualização bem-sucedida
- **WHEN** um usuário autenticado envia `{ "status": "EM_PREPARO" }` para um pedido em `RECEBIDO`
- **THEN** o sistema responde `200` com o pedido em `EM_PREPARO` e `updatedAt` atualizado

#### Scenario: Pedido inexistente
- **WHEN** o usuário atualiza o status de um UUID que não existe
- **THEN** o sistema responde `404` com `code` `ORDER_NOT_FOUND`

### Requirement: Validação do payload de atualização de status
O sistema MUST rejeitar com `400 Bad Request`, sem alterar o pedido, um payload de atualização de status vazio, sem `status`, com `status` nulo, com valor fora da lista de estados, com grafia diferente da oficial (minúsculas) ou com tipo não textual, e também um `{id}` que não seja UUID.

#### Scenario: Status ausente ou nulo
- **WHEN** o usuário envia `{}` ou `{ "status": null }`
- **THEN** o sistema responde `400` com `errors[].field` = `status`

#### Scenario: Status inexistente
- **WHEN** o usuário envia `{ "status": "PRONTO" }`, `{ "status": "recebido" }` ou `{ "status": 1 }`
- **THEN** o sistema responde `400` com `code` `MALFORMED_REQUEST`

#### Scenario: ID inválido
- **WHEN** o usuário chama `PATCH /api/v1/orders/abc/status`
- **THEN** o sistema responde `400` com `code` `INVALID_PARAMETER`

### Requirement: Histórico de status
Cada pedido MUST manter um histórico ordenado cronologicamente com uma entrada na criação (`from` nulo → `RECEBIDO`) e uma entrada para cada transição aceita, contendo estado de origem, estado de destino, usuário responsável e data/hora em UTC. Transições rejeitadas MUST NOT gerar entrada.

#### Scenario: Histórico após entrega
- **WHEN** um pedido passa por `RECEBIDO → EM_PREPARO → SAIU_PARA_ENTREGA → ENTREGUE`
- **THEN** `GET /api/v1/orders/{id}` retorna `history` com 4 entradas na ordem correta, cada uma com `changedBy` e `changedAt`

#### Scenario: Transição rejeitada não gera histórico
- **WHEN** uma transição inválida é tentada
- **THEN** o número de entradas em `history` não muda

### Requirement: Transições disponíveis
A consulta de um pedido MUST informar em `allowedTransitions` os estados para os quais ele pode ir a partir do status atual (lista vazia para estados finais).

#### Scenario: Transições a partir de EM_PREPARO
- **WHEN** um pedido em `EM_PREPARO` é consultado
- **THEN** `allowedTransitions` é `["SAIU_PARA_ENTREGA", "CANCELADO"]`

#### Scenario: Estado final
- **WHEN** um pedido em `ENTREGUE` é consultado
- **THEN** `allowedTransitions` é uma lista vazia

### Requirement: Atualizações concorrentes
Quando duas atualizações de status do mesmo pedido concorrem, o sistema MUST aplicar no máximo uma delas com base na versão lida; a outra MUST receber `409 Conflict` com `code` `CONCURRENT_MODIFICATION`.

#### Scenario: Conflito de versão
- **WHEN** dois operadores alteram simultaneamente o status do mesmo pedido a partir da mesma versão
- **THEN** uma requisição responde `200` e a outra `409` com `code` `CONCURRENT_MODIFICATION`
