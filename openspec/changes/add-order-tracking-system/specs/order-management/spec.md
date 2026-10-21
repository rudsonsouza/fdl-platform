# Spec Delta

## Purpose

Permite que operadores autenticados registrem pedidos de delivery (cliente, itens e endereço de entrega), listem todos os pedidos e consultem um pedido específico, com validação rigorosa de entrada e erros padronizados.

## ADDED Requirements

### Requirement: Criação de pedido
O sistema SHALL permitir que um usuário autenticado crie um pedido com dados do cliente (nome, telefone), uma lista de itens (nome, quantidade, preço unitário em centavos) e endereço de entrega. O pedido criado MUST ter identificador UUID, status `RECEBIDO`, total calculado pelo servidor e registro do usuário criador.

#### Scenario: Pedido criado com sucesso
- **WHEN** um usuário autenticado envia `POST /api/v1/orders` com cliente "Maria Silva"/"11987654321", itens [Pizza ×1 a 5490, Refrigerante ×2 a 1200] e endereço completo
- **THEN** o sistema responde `201 Created` com cabeçalho `Location: /api/v1/orders/{id}`, `status` = `RECEBIDO` e `totalCents` = 7890

#### Scenario: Total é sempre calculado pelo servidor
- **WHEN** o payload de criação inclui `totalCents`
- **THEN** o sistema responde `400` com `code` `MALFORMED_REQUEST` e nenhum pedido é criado

### Requirement: Validação do payload de criação de pedido
O sistema MUST rejeitar com `400 Bad Request` todo payload de criação inválido, sem persistir nada. As regras de cada campo estão nos cenários abaixo; campos desconhecidos ou somente-servidor (`id`, `status`, `totalCents`) não são aceitos.

#### Scenario: Corpo vazio ou JSON malformado
- **WHEN** o usuário envia `{}` ou um corpo que não é JSON válido
- **THEN** o sistema responde `400`; para `{}` os campos `customer`, `items` e `deliveryAddress` aparecem em `errors`

#### Scenario: Cliente inválido
- **WHEN** `customer` está ausente, `customer.name` está vazio ou fora de 2–100 caracteres, ou `customer.phone` não tem só dígitos com 10–11 posições (ex.: "11abc", "119876543", "119876543210")
- **THEN** o sistema responde `400` apontando o campo correspondente em `errors[].field`

#### Scenario: Lista de itens inválida
- **WHEN** `items` está ausente, vazia ou tem mais de 50 itens
- **THEN** o sistema responde `400` com `errors[].field` = `items`

#### Scenario: Item inválido
- **WHEN** um item não tem `name`, tem `quantity` 0, negativa, maior que 99, decimal (1.5) ou não numérica ("dois"), ou tem `unitPriceCents` ausente, negativo ou acima de 10000000
- **THEN** o sistema responde `400` apontando o campo do item (ex.: `items[0].quantity`)

#### Scenario: Endereço inválido
- **WHEN** `deliveryAddress` está ausente, `street`/`number`/`neighborhood`/`city` estão vazios ou acima de 120 caracteres, `state` não tem exatamente 2 letras maiúsculas ("sp", "SPA") ou `zipCode` não tem exatamente 8 dígitos ("01310-100", "123")
- **THEN** o sistema responde `400` apontando o campo correspondente (ex.: `deliveryAddress.zipCode`)

#### Scenario: Complemento é opcional
- **WHEN** o endereço é enviado sem `complement`
- **THEN** o pedido é criado normalmente

### Requirement: Listagem de pedidos
O sistema SHALL listar todos os pedidos para qualquer usuário autenticado, de forma paginada (padrão `page=0`, `size=100`, máximo 500), ordenada por data de criação decrescente por padrão, com filtro opcional por `status`. Cada item da lista MUST trazer id, cliente, status atual, total e datas de criação/atualização.

#### Scenario: Listar todos
- **WHEN** um usuário autenticado chama `GET /api/v1/orders` sem parâmetros e existem 30 pedidos
- **THEN** o sistema responde `200` com os 30 pedidos numa única página e metadados `page`, `size`, `totalElements`, `totalPages`

#### Scenario: Paginação explícita
- **WHEN** existem 150 pedidos e o usuário chama `GET /api/v1/orders?page=1&size=100`
- **THEN** o sistema retorna os 50 pedidos restantes com `totalElements` = 150 e `totalPages` = 2

#### Scenario: Filtrar por status
- **WHEN** o usuário chama `GET /api/v1/orders?status=EM_PREPARO`
- **THEN** somente pedidos com status `EM_PREPARO` são retornados

#### Scenario: Parâmetros de listagem inválidos
- **WHEN** o usuário envia `status=INVALIDO`, `page=-1`, `size=0`, `size=501` ou `sort=senha,asc` (campo fora da lista permitida)
- **THEN** o sistema responde `400` com `code` `INVALID_PARAMETER` ou `VALIDATION_ERROR`

### Requirement: Consulta de pedido por ID
O sistema SHALL retornar o pedido completo por ID — cliente, itens, endereço, total, status atual, histórico de status e transições permitidas a partir do status atual.

#### Scenario: Pedido encontrado
- **WHEN** um usuário autenticado chama `GET /api/v1/orders/{id}` com um ID existente
- **THEN** o sistema responde `200` com todos os dados do pedido, incluindo `history` e `allowedTransitions`

#### Scenario: Pedido inexistente
- **WHEN** o usuário chama `GET /api/v1/orders/{id}` com um UUID que não existe
- **THEN** o sistema responde `404 Not Found` com `code` `ORDER_NOT_FOUND`

#### Scenario: ID em formato inválido
- **WHEN** o usuário chama `GET /api/v1/orders/abc`
- **THEN** o sistema responde `400` com `code` `INVALID_PARAMETER`

### Requirement: Persistência durável
Pedidos e usuários MUST ser persistidos em banco SQLite e permanecer disponíveis após reinício da aplicação.

#### Scenario: Dados sobrevivem ao reinício
- **WHEN** um pedido é criado e a aplicação é reiniciada apontando para o mesmo arquivo de banco
- **THEN** `GET /api/v1/orders/{id}` retorna o mesmo pedido

### Requirement: Formato padronizado de erro
Toda resposta de erro da API MUST usar `Content-Type: application/problem+json` (RFC 9457) com `type`, `title`, `status`, `detail`, `instance`, um `code` estável e um `traceId`; erros de validação MUST incluir `errors` com `field` e `message`. Erros `500` MUST NOT expor stack trace.

#### Scenario: Erro de validação padronizado
- **WHEN** um payload inválido é enviado a qualquer endpoint
- **THEN** a resposta `400` tem `Content-Type: application/problem+json`, `code` e a lista `errors`

#### Scenario: Tipo de conteúdo não suportado
- **WHEN** um cliente envia `POST /api/v1/orders` com `Content-Type: text/plain`
- **THEN** o sistema responde `415 Unsupported Media Type` no formato padronizado
