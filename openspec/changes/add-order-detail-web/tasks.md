# Tasks

> Pré-requisito: `add-order-tracking-system` aplicada e arquivada. Trabalhar na branch `feat/frontend-order-detail` (ou direto na `main`), um commit por grupo em Conventional Commits.

## 1. Dados e componentes

- [ ] 1.1 Implementar `useOrder` (`refetchInterval` 15 s, pausado com aba oculta) e `useUpdateOrderStatus` (invalida `['orders']` e `['orders', id]`); verificar com testes de hooks usando MSW, incluindo `404` e `409`
- [ ] 1.2 Implementar `ConfirmDialog` sobre Radix `AlertDialog`; verificar com testes de foco preso, Esc, retorno de foco e `axe`
- [ ] 1.3 Implementar `StatusTimeline` (`<ol>`, `<time>`, `aria-current="step"`) e `StatusActions` (só `allowedTransitions`); verificar com testes por status e `axe`
- [ ] 1.4 Commitar (`feat(frontend): add order detail building blocks`); verificar com `git log`

## 2. Tela de detalhe

- [ ] 2.1 Implementar `OrderDetailPage` e rota `/orders/:id`, com links a partir da `OrderListPage`; verificar com testes de navegação lista → detalhe, foco no `h1` e título do documento
- [ ] 2.2 Integrar ações de status, confirmação de cancelamento, anúncios `aria-live` e tratamento de `404`/`409`; verificar com testes de cada cenário do spec e fluxo só por teclado
- [ ] 2.3 Rodar `npm run test:coverage` e verificar cobertura ≥ 70%; verificar CI verde
- [ ] 2.4 Atualizar o README (telas e roteiro manual com troca de status pela interface) e verificar seguindo o roteiro
- [ ] 2.5 Commitar (`feat(frontend): add order detail and status update UI`, `docs: update README`); verificar com `git log`

## Workflow follow-up

- Arquivar a mudança com `openspec archive add-order-detail-web` e criar a tag `v1.1.0`.
