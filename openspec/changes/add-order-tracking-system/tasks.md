# Tasks

> Convenção de versionamento (design D13): cada grupo termina com ao menos um commit em Conventional Commits citando as tarefas (`Refs: tasks X.Y`). Commits intermediários por tarefa são bem-vindos; nunca acumular grupos num único commit.

## 1. Repositório e versionamento

- [x] 1.1 Ampliar o `.gitignore` existente com as entradas de front-end, SQLite, segredos, SO/IDE e `Claude outputs/` (design D13); verificar com `git status` que `.DS_Store` e `data/` não aparecem após criar arquivos de teste nessas rotas
- [x] 1.2 Na branch `chore/repo-setup`, commitar `.gitignore`, `openspec/` e `.claude/` (`chore: add OpenSpec planning for order tracking`), fazer push e abrir PR para `main`; verificar a branch e o PR no GitHub (o merge acontece ao fim do grupo 1)
- [x] 1.3 Criar estrutura `backend/` e `frontend/` (com `.gitkeep`) e `.editorconfig`; verificar que os diretórios existem
- [x] 1.4 Definir a estratégia de branch (branch por grupo + PR, ou commits diretos na `main`) e registrar no README em "Contribuição"; verificar o texto no README

## 2. Fundação do back-end

- [x] 2.1 Gerar projeto no start.spring.io (Maven, Java 25, Spring Boot 4.x) com starters webmvc, validation, security, oauth2-resource-server, data-jpa, flyway, actuator e os starters de teste webmvc, data-jpa e security; adicionar `sqlite-jdbc`, `hibernate-community-dialects` e springdoc compatível com Boot 4; registrar no README as versões efetivas; verificar com `./mvnw -q compile`
- [x] 2.2 Configurar Surefire (unit), Failsafe (`*IT`) e JaCoCo com regra de 80% de linhas que falha o build; verificar que `./mvnw verify` executa os plugins
- [x] 2.3 Configurar perfis `dev`/`test` e variáveis `FDL_DB_PATH`, `FDL_JWT_SECRET`, `FDL_JWT_TTL`, `FDL_CORS_ORIGINS`, `FDL_SWAGGER_ENABLED`; verificar que a aplicação sobe e `/actuator/health` retorna `UP`
- [x] 2.4 Commitar como `chore(backend): scaffold Spring Boot 4 project`; verificar com `git log --oneline`

## 3. Persistência SQLite

- [x] 3.1 Configurar datasource SQLite com PRAGMAs (WAL, `foreign_keys`, `busy_timeout`) e pool de 1 conexão; verificar com teste que lê os PRAGMAs ativos
- [x] 3.2 Criar migração `V1__create_users.sql` (com `uk_users_email`) e verificar aplicação do zero em teste
- [x] 3.3 Criar migração `V2__create_orders.sql` (orders, order_items, order_status_history, `CHECK`s e índices) e verificar que `ddl-auto=validate` passa
- [x] 3.4 Criar base de teste `@DataJpaTest` reutilizável com SQLite em `@TempDir` + Flyway e verificar com um teste de fumaça
- [x] 3.5 Commitar como `feat(backend): add SQLite persistence and migrations`; verificar com `git log`

## 4. Domínio de pedidos e ciclo de status

- [x] 4.1 [TDD] Implementar `OrderStatus` com `allowedTransitions()`/`canTransitionTo`; verificar com teste parametrizado das 25 combinações origem×destino (spec `order-status-lifecycle`)
- [x] 4.2 Implementar entidades `Order` (`@Version`), `OrderItem`, `DeliveryAddress` (embeddable) e `OrderStatusHistory`; verificar com teste de repositório que persiste e relê um pedido completo
- [x] 4.3 [TDD] Implementar `Order.create(...)` com status `RECEBIDO`, histórico inicial `null→RECEBIDO` e `totalCents` calculado; verificar com testes unitários (total 7890 do exemplo do spec)
- [x] 4.4 [TDD] Implementar `Order#changeStatus(newStatus, userId, Clock)` com histórico e `InvalidStatusTransitionException` carregando `currentStatus`, `requestedStatus` e `allowedTransitions`; verificar com testes de transição válida, inválida, estado final e mesmo estado (histórico inalterado na falha)
- [x] 4.5 Implementar `OrderRepository` (busca paginada por status, fetch de itens/histórico no detalhe); verificar com testes de filtro, paginação, ordenação, cascade e `CHECK` de status
- [x] 4.6 Commitar (`feat(backend): add order domain and status state machine` + `test(backend): ...` se separado); verificar com `git log`

## 5. Tratamento de erros e validação transversal

- [x] 5.1 Configurar Jackson 3 explicitamente (design D6): `spring.jackson.deserialization.fail-on-unknown-properties=true`, `JsonMapperBuilderCustomizer` sem coerção float/string→inteiro, enums case-sensitive, `FAIL_ON_NULL_FOR_PRIMITIVES` ligado; verificar com testes de desserialização do `JsonMapper` da aplicação (campo extra, `1.5`, `"dois"`, `"recebido"` falham)
- [x] 5.2 Implementar `GlobalExceptionHandler` + `ErrorCode` com Problem Details (`code`, `errors[]`, `traceId`; no `409` de transição também `currentStatus`, `requestedStatus`, `allowedTransitions`) para todas as exceções do design D7; verificar com testes unitários de cada mapeamento e ausência de stack trace no 500
- [x] 5.3 Implementar filtro de `traceId` (MDC + `X-Trace-Id`); verificar com teste que o mesmo valor aparece no cabeçalho e no corpo de erro
- [x] 5.4 Commitar como `feat(backend): add problem-details error handling`; verificar com `git log`

## 6. Autenticação

- [x] 6.1 Implementar `User` + `UserRepository.findByEmailIgnoreCase`; verificar com teste de repositório da unicidade de e-mail
- [x] 6.2 Implementar `JwtTokenService` (Nimbus HS256, TTL configurável, falha de startup sem segredo); verificar com testes de claims, expiração e assinatura inválida
- [x] 6.3 Configurar `SecurityFilterChain` stateless, rotas públicas, BCrypt(12), CORS e `AuthenticationEntryPoint` em Problem Details; verificar com `SecurityTest` (sem token, expirado, adulterado → `401 UNAUTHENTICATED`; `/auth/**` públicas)
- [x] 6.4 [TDD] Implementar `AuthService.register` (e-mail normalizado, hash, `409` em duplicado) e `AuthService.login` (`401` genérico, tempo uniforme); verificar com testes unitários incluindo senha ≠ hash persistido
- [x] 6.5 Implementar DTOs `RegisterRequest`/`LoginRequest`/`TokenResponse` e `AuthController` (`/register`, `/login`, `/me`); verificar com `AuthControllerTest` dos caminhos felizes, `401` e `409`
- [x] 6.6 Escrever `AuthControllerValidationTest` (`@WebMvcTest`, parametrizado) cobrindo todos os cenários `400` dos requisitos de validação do spec `user-auth` (corpo vazio, JSON malformado, nome/e-mail/senha inválidos, campo desconhecido) e `415`; verificar `code`, `errors[].field` e `verifyNoInteractions(authService)`
- [x] 6.7 Verificar com teste de captura de logs que senha e token não aparecem em logs após login com falha
- [x] 6.8 Documentar endpoints de auth no OpenAPI (exemplos e erros) e verificar no `/v3/api-docs`
- [ ] 6.9 Commitar (`feat(backend): add registration, login and JWT security`); verificar com `git log`

## 7. API de pedidos

- [x] 7.1 Implementar DTOs (`CreateOrderRequest`, `CustomerRequest`, `OrderItemRequest`, `AddressRequest`, `UpdateStatusRequest`, `OrderResponse`, `OrderSummaryResponse`, `PageResponse`) com Bean Validation do spec `order-management` e `OrderMapper`; verificar com testes unitários do mapper
- [x] 7.2 [TDD] Implementar `OrderService` (`create` com `createdBy`, `findAll`, `findById` → `404`, `updateStatus` → `404`/`409`, conversão de `OptimisticLockingFailureException`); verificar com testes unitários com Mockito e `Clock` fixo
- [x] 7.3 Implementar `OrderController` (`POST /orders` com `Location`, `GET /orders` com allowlist de sort, `size` padrão 100 e máximo 500, `GET /orders/{id}` com `allowedTransitions`, `PATCH /orders/{id}/status`); verificar com `OrderControllerTest` dos casos `201/200/404/409` e de `GET /orders` sem parâmetros retornando todos os pedidos de teste numa página
- [x] 7.4 Escrever `CreateOrderValidationTest` (`@WebMvcTest`, parametrizado) cobrindo todos os cenários `400` do spec `order-management` (corpo vazio, JSON malformado, cliente, itens, item, endereço, `totalCents`/`id`/`status` enviados) e `415`; verificar `code`, `errors[].field` e `verifyNoInteractions(orderService)`
- [x] 7.5 Escrever `UpdateStatusValidationTest` cobrindo os cenários `400` do spec `order-status-lifecycle` (`{}`, `null`, `"PRONTO"`, `"recebido"`, `1`, id `abc`); verificar que o serviço não é chamado
- [x] 7.6 Escrever `ListAndGetOrderValidationTest` cobrindo `status=INVALIDO`, `page=-1`, `size=0`, `size=501`, `sort=senha,asc` e `GET /orders/abc`; verificar `400` com `code` correto
- [x] 7.7 Escrever teste de concorrência (duas atualizações sobre a mesma versão) e verificar uma resposta `200` e outra `409 CONCURRENT_MODIFICATION`
- [x] 7.8 Documentar endpoints de pedidos no OpenAPI, incluindo a matriz de transições e o significado de `400`/`409` em `PATCH /orders/{id}/status`; verificar no Swagger UI
- [ ] 7.9 Commitar (`feat(backend): add orders API` e `test(backend): cover invalid payloads with 400`); verificar com `git log`

## 8. Integração do back-end e CI

- [x] 8.1 Escrever `OrderFlowIT` (`@SpringBootTest`, SQLite temporário): cadastro → login → criar → listar → buscar → `EM_PREPARO` → `SAIU_PARA_ENTREGA` → `ENTREGUE` → cancelar (`409` com `allowedTransitions` vazio); verificar `history` com 4 entradas
- [x] 8.2 Escrever `PersistenceRestartIT` que cria pedido, reinicia o contexto com o mesmo arquivo e verifica que o pedido é retornado
- [x] 8.3 Rodar `./mvnw verify` e verificar build verde com cobertura JaCoCo ≥ 80%
- [ ] 8.4 Criar `.github/workflows/ci.yml` com o job `backend` (Temurin 25, cache Maven, `./mvnw verify`); verificar execução verde no GitHub Actions após o push
- [ ] 8.5 Commitar (`test(backend): add integration tests` e `ci: add backend workflow`); verificar com `git log`

## 9. Fundação do front-end

- [ ] 9.1 Criar projeto Vite + React 19 + TypeScript `strict`, ESLint (+ `jsx-a11y`) e Prettier; verificar com `npm run build` e `npm run lint`
- [ ] 9.2 Configurar Vitest + Testing Library + `user-event` + MSW + `vitest-axe`, `renderWithProviders` e meta de cobertura 70%; verificar com um teste de fumaça
- [ ] 9.3 Implementar `apiClient` + `ApiError` (Problem Details tipado, `Authorization`, `401` → logout); verificar com testes unitários usando MSW
- [ ] 9.4 Implementar `useAuthStore` (Zustand, `sessionStorage`, logout por `exp`); verificar com testes unitários
- [ ] 9.5 Configurar providers, router (`/login`, `/register`, `/orders`, `/orders/new`) e `ProtectedRoute` com `redirect`; verificar com testes de redirecionamento sem sessão e após `401`
- [ ] 9.6 Implementar `AppShell` (landmarks, `SkipLink`, `LiveRegion`, `useRouteAnnouncer`, ação "Sair"); verificar com testes de foco no `h1`, título do documento, skip link, logout e `axe` sem violações
- [ ] 9.7 Implementar componentes `Button` (estado pendente com `aria-disabled`), `TextField`, `FormErrorSummary`, `Spinner`, `StatusBadge`; verificar cada um com teste de teclado/ARIA e `axe`
- [ ] 9.8 Criar handlers MSW para os endpoints usados (sucesso e `400/401/409`); verificar uso nos testes das telas
- [ ] 9.9 Adicionar o job `frontend` ao CI (`npm ci`, `lint`, `test:coverage`, `build`); verificar execução verde no GitHub Actions
- [ ] 9.10 Commitar (`feat(frontend): scaffold app shell, session and api client`, `ci: add frontend workflow`); verificar com `git log`

## 10. Telas de autenticação

- [ ] 10.1 Implementar schemas Zod de cadastro/login espelhando o spec `user-auth`; verificar com testes de cada caso inválido
- [ ] 10.2 Implementar `RegisterPage` e `LoginPage` (React Hook Form, `autocomplete`, erros por campo, resumo focado, erro do servidor em `role="alert"`, cadastro → login com e-mail preenchido); verificar com testes de validação, mapeamento de `400/401/409`, redirecionamento pós-login, navegação por teclado e `axe`
- [ ] 10.3 Commitar (`feat(frontend): add login and registration`); verificar com `git log`

## 11. Telas de pedidos (lista e criação)

- [ ] 11.1 Implementar hooks `useOrders` (`refetchOnWindowFocus`) e `useCreateOrder` (invalidação de `['orders']`); verificar com testes de hooks usando MSW
- [ ] 11.2 Implementar `OrderListPage` (tabela com `caption`/`scope`, status atual via `StatusBadge`, filtro na URL, paginação quando necessário, botão "Atualizar", `aria-busy`, vazio, erro); verificar com testes de status exibido, filtro na URL, estados e `axe`
- [ ] 11.3 Implementar `NewOrderPage` (`fieldset`s, `useFieldArray` com anúncio ao adicionar/remover, total em BRL, erros `400` do servidor nos campos, bloqueio de envio duplicado, retorno à lista com anúncio "Pedido criado"); verificar com testes de envio, total, validação, foco no resumo, anúncio de remoção, envio duplicado, fluxo só por teclado e `axe`
- [ ] 11.4 Rodar `npm run test:coverage` e verificar cobertura ≥ 70%
- [ ] 11.5 Commitar (`feat(frontend): add order list and creation`); verificar com `git log`

## 12. Entrega para avaliação

- [ ] 12.1 Criar `backend/Dockerfile` (multi-stage Maven → JRE 25) e `frontend/Dockerfile` (build Vite → nginx); verificar com `docker build` de cada um
- [ ] 12.2 Criar `docker-compose.yml` (volume `fdl-data`, `.env`, portas 8080/5173) e `.env.example`; verificar com `docker compose up --build` que login e lista funcionam no navegador
- [ ] 12.3 Reescrever `README.md` com todas as seções do design D14 (incluindo roteiro `curl` com um `400` e um `409`, diagrama de status, decisões/trade-offs e "Uso de IA"); verificar executando o roteiro `curl` exatamente como escrito
- [ ] 12.4 Commitar (`chore: add docker compose setup`, `docs: write README for reviewers`); verificar com `git log`

## 13. Verificação integrada

- [ ] 13.1 Cruzar cada cenário `400` dos specs com um teste existente (checklist no PR ou no README) e verificar que nenhum está sem cobertura
- [ ] 13.2 Executar a partir de um clone limpo do GitHub: `docker compose up --build`, cadastro → login → criar pedido → ver na lista com status `Recebido` → alterar status via `curl` → "Atualizar" mostra o novo status; reiniciar os contêineres e verificar persistência
- [ ] 13.3 Verificar acessibilidade manual: todas as telas só por teclado, leitor de tela (VoiceOver/NVDA) nos formulários e na lista, zoom 200%
- [ ] 13.4 Revisar segurança (sem segredos versionados, `.env` ignorado, senha/token fora de logs, CORS restrito) e verificar com `git grep` por segredos e nos logs da execução
- [ ] 13.5 Verificar o histórico com `git log --oneline --graph`: commits incrementais por grupo, mensagens em Conventional Commits, CI verde no último commit da `main`

## Workflow follow-up

- Arquivar a mudança com `openspec archive add-order-tracking-system` em commit próprio (`docs: archive order tracking change`).
- Criar a tag `v1.0.0` na `main` e fazer push da tag.
- Opcional, se houver tempo: aplicar a mudança `add-order-detail-web` (detalhe do pedido e troca de status pela interface) e publicar como `v1.1.0`.
