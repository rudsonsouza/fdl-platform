# Design

## Context

Motivação e escopo em `proposal.md`; comportamento exigido nos specs `user-auth`, `order-management`, `order-status-lifecycle` e `order-tracking-web`.

Estado atual: repositório Git `github.com/rudsonsouza/fdl-platform`, branch `main`, um commit inicial com `README.md` (2 linhas) e um `.gitignore` só de Maven/Eclipse. Não há código nem specs; o planejamento OpenSpec (`openspec/`, `.claude/`) foi copiado para a raiz do repositório e ainda não foi commitado.

Restrições do desafio: back-end **Java + Spring Boot** (escolhido Java 25), persistência em **SQLite**, front-end **React** que lista pedidos com status atual e cria pedidos, e **repositório Git com histórico de commits**. Restrições adicionais do autor: WAI-ARIA e testes obrigatórios com cobertura dos cenários `400`.

Visão geral:

```
┌──────────────────────────┐   JSON + Bearer JWT    ┌──────────────────────────────┐
│ frontend/ React SPA      │ ─────────────────────► │ backend/ Spring Boot 4.x     │
│ Vite · TS · TanStack Q.  │                        │ Java 25 · /api/v1            │
└──────────────────────────┘                        │ Security(JWT) · JPA · Flyway │
                                                    └──────────────┬───────────────┘
                                                                   │ sqlite-jdbc
                                                           ┌───────▼────────┐
                                                           │ SQLite (WAL)   │
                                                           └────────────────┘
```

Layout alvo do repositório:

```
fdl-platform/
├── .github/workflows/ci.yml
├── .claude/              # comandos/skills OpenSpec usados com IA
├── openspec/             # proposal, specs, design, tasks
├── backend/              # Maven wrapper, Spring Boot
├── frontend/             # Vite, React
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

## Goals / Non-Goals

**Goals:**
- Regras de negócio (transições de status, cálculo de total) isoladas no domínio, testáveis sem Spring.
- Contrato de erro único (Problem Details) usado por todos os endpoints e consumido de forma tipada pelo front.
- Validação declarativa no back e espelhada no front, com os mesmos limites.
- Schema reprodutível por migrações; nenhum `ddl-auto=update`.
- Front com separação clara entre estado de servidor, sessão e estado local de UI.
- Histórico Git legível, que mostre a evolução incremental (planejamento → back → front → entrega).
- Avaliador consegue rodar tudo com `docker compose up` ou seguindo o README, sem adivinhação.
- Gates automáticos: cobertura ≥ 80% (back, JaCoCo) e ≥ 70% (front, Vitest); `axe` sem violações; CI verde.

**Non-Goals:**
- Escalabilidade horizontal ou alta concorrência de escrita (SQLite é um escritor por vez).
- Autorização por papel, multi-tenant, refresh token.
- Tela de detalhe e troca de status pela interface (mudança opcional `add-order-detail-web`).
- Deploy em nuvem.

## Decisions

### D1. Spring Boot 4.x + Java 25, organização por feature
Pacotes `auth`, `user`, `order` (`api`, `domain`, `service`, `repository`) e `shared` (`config`, `error`). Controllers só traduzem HTTP ↔ DTO (`record`s) e nunca expõem entidades; services são `@Transactional`; o domínio não depende de Spring.
Boot 4 é modular: gerar o projeto no start.spring.io (Java 25, Maven) e usar os starters e módulos de teste que ele indicar — `spring-boot-starter-webmvc` (o `-web` antigo pode estar deprecado), `-validation`, `-security`, `-oauth2-resource-server`, `-data-jpa`, `-flyway`, `-actuator` e os starters de teste por tecnologia (`-webmvc-test`, `-data-jpa-test`, `-security-test`). Os nomes exatos devem ser confirmados na geração (tarefa 2.1).
*Alternativas:* pacotes por camada — dispersa cada feature; hexagonal completa — cerimônia excessiva; Spring Boot 3.5 + Java 21 — mais maduro, mas o autor optou por Java 25 (Boot 3.5 também roda em 25 e é o plano B se alguma dependência falhar).

### D2. SQLite com Hibernate community dialect + Flyway
`org.xerial:sqlite-jdbc` + `org.hibernate.community.dialect.SQLiteDialect`; schema por Flyway (`V1__create_users.sql`, `V2__create_orders.sql`) e `ddl-auto=validate`. PRAGMAs na conexão: `journal_mode=WAL`, `foreign_keys=ON`, `busy_timeout=5000`; HikariCP com `maximum-pool-size=1`. Testes usam arquivo em `@TempDir` (não `:memory:`, que não é compartilhado entre conexões). O arquivo do banco fica em `data/` (ignorado pelo Git; volume no Docker).
*Alternativas:* JdbcTemplate — mais mapeamento manual; `ddl-auto=update` — schema não versionado e frágil no dialeto comunitário; H2 — "similar" é aceito, mas SQLite é o pedido explícito.

Modelo:

| Tabela | Colunas principais |
|--------|-------------------|
| `users` | `id TEXT PK`, `name`, `email` (`uk_users_email`), `password_hash`, `created_at`, `updated_at` |
| `orders` | `id TEXT PK`, `customer_name`, `customer_phone`, `street`, `number`, `complement?`, `neighborhood`, `city`, `state`, `zip_code`, `status` (`CHECK` nos 5 valores), `total_cents INTEGER`, `created_by → users`, `created_at`, `updated_at`, `version` |
| `order_items` | `id`, `order_id → orders ON DELETE CASCADE`, `name`, `quantity` (`CHECK > 0`), `unit_price_cents` (`CHECK >= 0`) |
| `order_status_history` | `id`, `order_id → orders`, `from_status?`, `to_status`, `changed_by → users`, `changed_at` |

Índices: `idx_orders_status_created_at (status, created_at)`, `idx_order_items_order_id`, `idx_order_status_history_order_id`. Datas ISO-8601 UTC (`TEXT`).

### D3. IDs UUID e dinheiro em centavos
IDs gerados no domínio (`UUID` como `TEXT`); dinheiro como `long` em centavos (`*Cents` na API).
*Alternativas:* `AUTOINCREMENT` — enumerável; `BigDecimal`/`REAL` — SQLite não tem DECIMAL e `REAL` tem erro de ponto flutuante.

### D4. Máquina de estados no enum de domínio
`OrderStatus` define `allowedTransitions()` (via `switch` exaustivo); `Order#changeStatus(newStatus, userId, Clock)` valida, altera e anexa `OrderStatusHistory`; falha lança `InvalidStatusTransitionException` → `409`. `Order` tem `@Version` → `OptimisticLockingFailureException` → `409 CONCURRENT_MODIFICATION`. `Clock` injetado para testes determinísticos.
O enunciado não define transições, então a regra é **comunicada explicitamente** para não parecer bug: o `409` traz `currentStatus`, `requestedStatus` e `allowedTransitions`; o Swagger descreve a matriz; o README tem um diagrama.
*Alternativas:* aceitar qualquer transição — mais simples, mas permite "voltar" de `ENTREGUE`; Spring Statemachine — pesado para 5 estados.

### D5. Transição inválida = `409`, payload inválido = `400`
`400` quando o payload não respeita o contrato (incluindo status inexistente); `409` quando o payload é válido mas conflita com o estado atual.
*Alternativas:* `422` — menos comum no ecossistema Spring; `400` para tudo — mistura erro do cliente com conflito de estado.

### D6. Validação estrita na borda (Jackson 3)
Bean Validation nos DTOs (`@NotBlank`, `@Size`, `@Pattern`, `@Min/@Max`, `@Valid` aninhado, `@Size(min=1,max=50)` em `items`); validação de métodos para query params; allowlist de ordenação (`createdAt`, `updatedAt`, `status`, `totalCents`); `size` padrão 100, máximo 500.
**Spring Boot 4 usa Jackson 3** (pacote `tools.jackson.*`, `JsonMapper` imutável). Consequências:
- O Spring Boot **desliga `FAIL_ON_UNKNOWN_PROPERTIES` por padrão**; é preciso religar (`spring.jackson.deserialization.fail-on-unknown-properties=true`) — sem isso `totalCents`/`role` extras seriam ignorados e os testes `400` correspondentes falhariam.
- Personalização via `JsonMapperBuilderCustomizer` (não mais `Jackson2ObjectMapperBuilderCustomizer`): desativar coerção float→inteiro e string→número (`withCoercionConfig` para `LogicalType.Integer`) e manter enums case-sensitive.
- Alguns padrões mudaram no Jackson 3 (ex.: `FAIL_ON_NULL_FOR_PRIMITIVES`, datas não serializadas como timestamp); a configuração é declarada explicitamente e coberta por testes de desserialização (tarefa 4.1) em vez de confiar nos padrões.
- Exceções de desserialização do Jackson 3 continuam chegando ao controller como `HttpMessageNotReadableException`.
*Alternativa:* forçar Jackson 2 no Boot 4 — suportado de forma transitória/deprecada; não vale o débito.

### D7. Erros como Problem Details (RFC 9457)
`GlobalExceptionHandler` (`@RestControllerAdvice`) estende o `ProblemDetail` nativo com `code`, `errors[]` e `traceId`:

| Origem | Status | `code` |
|--------|--------|--------|
| `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException` | 400 | `VALIDATION_ERROR` |
| `HttpMessageNotReadableException` (JSON inválido, tipo errado, enum inválido, campo desconhecido) | 400 | `MALFORMED_REQUEST` |
| `MethodArgumentTypeMismatchException` (UUID/enum em path ou query) | 400 | `INVALID_PARAMETER` |
| `HttpMediaTypeNotSupportedException` | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| `BadCredentialsException` | 401 | `INVALID_CREDENTIALS` |
| `AuthenticationEntryPoint` (sem token/token inválido) | 401 | `UNAUTHENTICATED` |
| `OrderNotFoundException` | 404 | `ORDER_NOT_FOUND` |
| `EmailAlreadyRegisteredException` | 409 | `EMAIL_ALREADY_REGISTERED` |
| `InvalidStatusTransitionException` / `OptimisticLockingFailureException` | 409 | `INVALID_STATUS_TRANSITION` (+ `currentStatus`, `requestedStatus`, `allowedTransitions`) / `CONCURRENT_MODIFICATION` |
| Demais | 500 | `INTERNAL_ERROR` (sem stack trace) |

Um filtro grava `traceId` no MDC, no cabeçalho `X-Trace-Id` e no corpo de erro.

### D8. Autenticação JWT stateless com o Resource Server do Spring
`SecurityFilterChain` stateless (CSRF desligado — não há cookie de sessão); públicas: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `/actuator/health` e Swagger (habilitado por padrão para facilitar a avaliação; desligável por `FDL_SWAGGER_ENABLED=false`). Token HS256 via `NimbusJwtEncoder`/`NimbusJwtDecoder` (claims `sub`, `email`, `name`, `iss`, `iat`, `exp`; TTL `FDL_JWT_TTL`, padrão 60 min). `FDL_JWT_SECRET` (≥ 256 bits) obrigatório fora do perfil `test`. Senhas com `BCryptPasswordEncoder(12)`; para e-mail inexistente executa-se `matches` contra hash fictício para igualar o tempo de resposta. CORS restrito a `FDL_CORS_ORIGINS`.
**Trade-off aceito:** o login não revela se o e-mail existe, mas o cadastro responde `409 EMAIL_ALREADY_REGISTERED`, o que revela. Para o desafio a usabilidade prevalece; em produção, o cadastro responderia sempre `202` e confirmaria por e-mail.
*Alternativas:* sessão com cookie — exige CSRF e estado no servidor; `jjwt` — dependência extra sem ganho.

Contrato resumido (`/api/v1`):

| Método | Rota | Sucesso |
|--------|------|---------|
| POST | `/auth/register` | `201 {id,name,email}` |
| POST | `/auth/login` | `200 {accessToken,tokenType,expiresIn}` |
| GET | `/auth/me` | `200 {id,name,email}` |
| POST | `/orders` | `201` + `Location` + pedido |
| GET | `/orders?status&page&size&sort` | `200 {content[],page,size,totalElements,totalPages}` (size padrão 100) |
| GET | `/orders/{id}` | `200` pedido + `history[]` + `allowedTransitions[]` |
| PATCH | `/orders/{id}/status` | `200` pedido atualizado |

### D9. Front-end: React 19 + TypeScript + Vite, escopo do desafio
Telas obrigatórias: `/login`, `/register`, `/orders` (lista com status atual) e `/orders/new` (criação). Tela de detalhe e troca de status pela interface ficam na mudança opcional `add-order-detail-web`; a arquitetura abaixo já comporta essa extensão sem refatoração.
```
src/
├── app/        # router, providers, AppShell
├── features/
│   ├── auth/   # api, schemas (Zod), hooks, pages
│   └── orders/ # api, schemas, types, hooks, components, pages
├── shared/     # api (apiClient, ApiError, queryKeys), ui, a11y, lib
└── test/       # setup, MSW handlers, renderWithProviders
```
Regras: componentes não chamam `fetch` (só hooks); `features/*` só se comunicam via `shared/`; TypeScript `strict`, sem `any`; tipos derivados dos schemas Zod.
*Alternativas:* Next.js — SSR desnecessário para SPA autenticada; CRA — descontinuado.

### D10. Gestão de estado em três camadas
- **Servidor** — TanStack Query: chave `['orders', filtros]`; `useCreateOrder` invalida `['orders']`; `refetchOnWindowFocus` ativo e botão "Atualizar" para refletir status atuais alterados via API.
- **Sessão** — store Zustand `useAuthStore` (`token`, `user`, `login`, `logout`) persistida em `sessionStorage`; logout automático pelo `exp` do JWT.
- **UI/local** — React Hook Form (+ `useFieldArray` para itens) e `useState`; filtro e página na query string.

`apiClient` (wrapper de `fetch`) injeta `Authorization`, converte `application/problem+json` em `ApiError` tipado (`status`, `code`, `fieldErrors`) e, em `401`, chama `logout()` e redireciona.
*Alternativas:* Redux Toolkit/RTK Query — mais boilerplate; Context puro para dados do servidor — reimplementa cache.

### D11. Acessibilidade por construção
HTML semântico primeiro; ARIA só onde o HTML não basta. Componentes compartilhados: `TextField` (label, `aria-invalid`, `aria-describedby`), `FormErrorSummary` (focável, `role="alert"`, links para campos), `LiveRegion` global, `SkipLink`, `useRouteAnnouncer` (título + foco no `h1`), `StatusBadge` (texto + cor AA), `Button` com estado pendente (`aria-disabled`). ESLint `jsx-a11y` e `vitest-axe`. Requisitos em `specs/order-tracking-web/spec.md`.
*Alternativa:* MUI — mais peso e menos controle sobre o markup.

### D12. Estratégia de testes
| Camada | Ferramentas | Foco |
|--------|-------------|------|
| Domínio | JUnit 5, AssertJ | Matriz 5×5 de transições, histórico, total |
| Serviço | JUnit 5, Mockito | `AuthService`, `OrderService`, `JwtTokenService` |
| Web (fatia) | `@WebMvcTest` (módulo de teste webmvc do Boot 4), MockMvc, Spring Security Test | **Todos os cenários `400`** dos specs via `@ParameterizedTest`, verificando `status`, `Content-Type`, `code`, `errors[].field` e `verifyNoInteractions(service)`; `401/404/409/415` |
| Jackson | Teste do `JsonMapper` configurado | Campos desconhecidos, coerções e enums (D6) |
| Repositório | `@DataJpaTest` (módulo de teste data-jpa) + SQLite `@TempDir` + Flyway | Constraints, cascade, filtro/paginação |
| Integração | `@SpringBootTest(RANDOM_PORT)` | Fluxo ponta a ponta e persistência após reinício |
| Front | Vitest, Testing Library, `user-event`, MSW, `vitest-axe` | Schemas, `apiClient`, hooks, páginas, teclado, axe |

Os testes `400` são escritos junto com cada endpoint, conforme `tasks.md`.

### D13. Versionamento e fluxo de commits
- Trabalho em branches curtas por grupo de tarefas (`feat/backend-auth`, `feat/frontend-orders`…) integradas na `main` por PR (squash desligado para preservar o histórico) — ou commits diretos na `main` se o autor preferir; em ambos os casos, **um commit por tarefa ou grupo coeso**, nunca um commit único no final.
- Mensagens em Conventional Commits (`chore:`, `feat(backend):`, `test(backend):`, `feat(frontend):`, `docs:`, `ci:`), corpo citando a tarefa (`Refs: tasks 5.4`).
- Primeiro commit da série: `.gitignore` ampliado + `openspec/` + `.claude/` (o planejamento feito com IA fica auditável).
- `.gitignore` acrescenta ao existente: `node_modules/`, `dist/`, `coverage/`, `*.local`, `.vite/`, `data/`, `*.db`, `*.db-wal`, `*.db-shm`, `.env`, `.env.*` (exceto `.env.example`), `.DS_Store`, `.idea/`, `*.iml`, `.vscode/`, `Claude outputs/`. O `.mvn/wrapper/maven-wrapper.jar` já ignorado é compatível com o wrapper no modo só-script.
- Tag `v1.0.0` na entrega; `openspec archive` da mudança antes da tag, como commit próprio.
- CI (`.github/workflows/ci.yml`): jobs `backend` (`setup-java` Temurin 25, `./mvnw verify`) e `frontend` (`setup-node` LTS, `npm ci`, `lint`, `test:coverage`, `build`) em push e PR.
*Alternativas:* Git Flow completo — cerimônia excessiva para um desafio individual.

### D14. Entrega para avaliação: README e Docker Compose
- `docker-compose.yml` com `backend` (build multi-stage: Maven + Temurin 25 → JRE 25; volume `fdl-data:/app/data`; `FDL_JWT_SECRET` lido de `.env`) e `frontend` (build Vite → nginx servindo o `dist/`, `VITE_API_BASE_URL` em build-time). `docker compose up --build` sobe tudo em `http://localhost:5173` / `:8080`.
- `.env.example` com todas as variáveis e comando para gerar o segredo (`openssl rand -base64 32`).
- `README.md` substitui o atual e contém: visão geral e stack; pré-requisitos; como rodar com Docker e sem Docker; como rodar os testes e ver cobertura; roteiro com `curl` (cadastro → login → criar → listar → buscar → atualizar status, incluindo um `400` e um `409`); link do Swagger; diagrama das transições de status; decisões e trade-offs (D4, D5, D8, paginação); estrutura de pastas; seção **Uso de IA** (OpenSpec + Claude: proposta → specs → design → tasks → implementação guiada, revisão humana de cada etapa).

## Risks / Trade-offs

- [SQLite aceita um escritor por vez → `SQLITE_BUSY` sob concorrência] → WAL, `busy_timeout`, pool de 1 conexão; aceitável para o MVP.
- [Dialeto comunitário do Hibernate pode ter incompatibilidades] → tipos simples, `ddl-auto=validate`, testes de repositório contra SQLite real.
- [Boot 4 / Java 25 / Jackson 3 são recentes → bibliotecas ou padrões podem divergir do esperado] → confirmar versões e starters na geração do projeto; configuração Jackson explícita e testada; plano B: Boot 3.5 em Java 25.
- [Avaliador sem JDK 25/Node instalados] → `docker compose up --build` e CI verde como prova de que o build funciona.
- [Regra de transição pode parecer bug para quem testa livremente] → `409` autoexplicativo, Swagger e README documentam a matriz.
- [Paginação pode esconder pedidos de quem espera "todos"] → página padrão de 100, metadados `totalElements`, documentado no README.
- [Token em `sessionStorage` é legível por script em caso de XSS] → TTL curto, React escapando saída, sem `dangerouslySetInnerHTML`, CSP no `index.html`; evolução: cookie `HttpOnly`.
- [Cadastro revela e-mails existentes] → trade-off aceito e documentado (D8).
- [Histórico Git pobre se tudo for commitado no fim] → tarefas exigem commit ao final de cada grupo; verificação final confere `git log`.

## Migration Plan

Não há sistema anterior nem dados a migrar. Execução:
1. `cp .env.example .env` e definir `FDL_JWT_SECRET`.
2. `docker compose up --build` **ou** `./mvnw spring-boot:run` em `backend/` + `npm ci && npm run dev` em `frontend/`.
3. Flyway cria o schema no primeiro start.

Rollback: parar a aplicação e remover/restaurar o arquivo SQLite (ou o volume `fdl-data`); migrações futuras serão sempre aditivas.

## Open Questions

- Política de retenção/backup do arquivo SQLite (irrelevante para o desafio local).
- Textos finais da interface (podem ser ajustados sem mudar comportamento).
