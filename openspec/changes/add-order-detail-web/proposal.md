# Proposal

## Why

O desafio exige apenas que o front-end liste pedidos com status atual e crie pedidos; a troca de status hoje só é possível pela API. Esta mudança **opcional** (diferencial) leva o rastreamento para a interface: ver o detalhe de um pedido, sua linha do tempo e avançar ou cancelar o status sem usar `curl`. Deve ser aplicada **somente depois** de `add-order-tracking-system` estar concluída e arquivada, e apenas se houver tempo.

## What Changes

- Nova tela `/orders/:id` com dados do pedido, itens, total, status atual e linha do tempo do histórico.
- Ações de status na interface restritas às `allowedTransitions` retornadas pela API; cancelamento com diálogo de confirmação acessível.
- Tratamento de `404` (pedido inexistente) e `409` (pedido alterado por outro operador ou transição recusada).
- Atualização periódica do detalhe (no máximo a cada 15 s) para refletir mudanças de outros operadores.
- Linhas da lista passam a levar ao detalhe.
- Nenhuma mudança no back-end: usa `GET /orders/{id}` e `PATCH /orders/{id}/status` já existentes.

## Capabilities

### New Capabilities
- Nenhuma.

### Modified Capabilities
- `order-tracking-web`: acrescenta detalhe do pedido, troca de status e diálogo de confirmação, com os respectivos requisitos de acessibilidade.

## Impact

- **Código**: apenas `frontend/` — `features/orders` (hooks `useOrder`, `useUpdateOrderStatus`; componentes `StatusTimeline`, `StatusActions`; `OrderDetailPage`) e `shared/ui` (`ConfirmDialog`).
- **Dependências**: `@radix-ui/react-alert-dialog` para o diálogo acessível.
- **Pré-requisito**: `add-order-tracking-system` aplicada e arquivada (o spec `order-tracking-web` precisa existir em `openspec/specs/`).
