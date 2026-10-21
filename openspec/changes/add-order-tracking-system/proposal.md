# Proposal

## Why

Operações de delivery precisam registrar pedidos e acompanhar cada etapa até a entrega, mas hoje não há um sistema único para isso: o status fica espalhado e não existe histórico confiável de quem mudou o quê e quando. Esta mudança entrega o **Mini Rastreador de Pedidos** do desafio técnico — API REST, front-end web e acesso restrito a usuários autenticados — versionado em Git com histórico que mostre a evolução do trabalho.

## What Changes

- **Repositório e versionamento** (obrigatório): o trabalho é feito no repositório já existente `github.com/rudsonsouza/fdl-platform` (branch `main`, commit inicial com `README.md` e `.gitignore` de Maven). O `.gitignore` é ampliado para front-end, SQLite, segredos e arquivos de SO/IDE; os artefatos OpenSpec são versionados junto com o código; cada tarefa (ou grupo de tarefas) vira um commit no padrão Conventional Commits; CI no GitHub Actions roda os testes a cada push/PR.
- **Back-end** `backend/`: API REST em **Java 25 + Spring Boot 4.x**, persistência em **SQLite** com migrações versionadas, documentação OpenAPI.
- **Autenticação**: cadastro (nome, e-mail, senha), login por e-mail e senha com emissão de token JWT; todos os demais endpoints exigem autenticação.
- **Pedidos**: criar pedido (cliente, itens, endereço de entrega), listar todos (paginado com página padrão de 100 itens, filtro por status) e buscar por ID.
- **Ciclo de status**: atualizar o status entre `RECEBIDO`, `EM_PREPARO`, `SAIU_PARA_ENTREGA`, `ENTREGUE` e `CANCELADO`, respeitando transições válidas e registrando histórico.
- **Validação e erros padronizados**: payloads inválidos rejeitados com `400 Bad Request` no formato Problem Details (RFC 9457).
- **Front-end** `frontend/`: SPA em **React + TypeScript** com o escopo exigido pelo desafio — login, cadastro, **lista de pedidos com status atual** e **criação de pedido** — com arquitetura por feature, gestão de estado explícita e acessibilidade **WAI-ARIA**.
- **Entrega para avaliação**: `README.md` completo (como rodar, variáveis, roteiro de teste com `curl`, decisões, uso de IA) e execução com um comando via `docker compose up`.
- **Qualidade**: testes unitários e de integração obrigatórios em ambos os lados, com cobertura explícita de todos os cenários de payload inválido → `400`.

Decisões de comportamento assumidas (detalhadas em `design.md` e documentadas no README e no Swagger):
- O usuário autenticado é um **operador**; o **cliente** é um dado do pedido. Todo operador vê todos os pedidos.
- O enunciado não define transições; adotamos um fluxo linear: cancelamento só em `RECEBIDO` ou `EM_PREPARO`, sem pular nem voltar etapas. Transição inválida retorna `409 Conflict` (payload válido, conflito com o estado atual).
- O cadastro com e-mail já existente retorna `409`, o que revela que o e-mail está cadastrado. É um trade-off consciente de usabilidade, aceitável no desafio.

Fora do escopo desta mudança:
- **Diferenciais de front-end** (tela de detalhe, troca de status pela interface, linha do tempo, atualização periódica) — planejados separadamente na mudança opcional `add-order-detail-web`, a ser aplicada só depois desta e se houver tempo.
- Papéis/perfis, refresh token, recuperação de senha, edição de itens/endereço, pagamentos, notificações em tempo real e deploy em nuvem.

## Capabilities

### New Capabilities
- `user-auth`: cadastro de usuário, login por e-mail/senha, emissão de token e exigência de autenticação para acessar o sistema.
- `order-management`: criação, listagem e consulta de pedidos, com validação de payload e formato de erro padronizado.
- `order-status-lifecycle`: estados do pedido, transições permitidas, atualização de status e histórico de rastreamento.
- `order-tracking-web`: aplicação web para autenticar, listar pedidos com status atual e criar pedidos, com requisitos de acessibilidade WAI-ARIA.

### Modified Capabilities
- Nenhuma (projeto novo, sem specs existentes).

## Impact

- **Repositório**: amplia `.gitignore` e `README.md` existentes; adiciona `backend/`, `frontend/`, `openspec/`, `.claude/`, `docker-compose.yml`, `.env.example` e `.github/workflows/ci.yml`.
- **APIs**: novos endpoints sob `/api/v1` — `/auth/register`, `/auth/login`, `/auth/me`, `/orders`, `/orders/{id}`, `/orders/{id}/status`.
- **Dados**: novo arquivo SQLite (`data/fdl.db`, fora do Git) com tabelas `users`, `orders`, `order_items`, `order_status_history`.
- **Dependências**: Spring Boot 4.x (web MVC, validation, security, oauth2-resource-server, data-jpa, actuator, Jackson 3), `sqlite-jdbc`, `hibernate-community-dialects`, Flyway, springdoc; no front, React Router, TanStack Query, Zustand, React Hook Form, Zod, Vitest, Testing Library, MSW, `vitest-axe`; Docker e Docker Compose para a execução simplificada.
- **Configuração**: variáveis `FDL_JWT_SECRET` (obrigatória), `FDL_DB_PATH`, `FDL_JWT_TTL`, `FDL_CORS_ORIGINS`, `VITE_API_BASE_URL`, documentadas em `.env.example`.
