# fdl-platform
Food Delivery Challenge

## Contribuição

### Estratégia de branch

- A `main` contém apenas trabalho revisado e integrado por Pull Request; não há commits diretos nela.
- Cada grupo de tarefas do `openspec/changes/add-order-tracking-system/tasks.md` é desenvolvido numa branch curta, nomeada pelo tipo e pelo escopo: `chore/repo-setup`, `feat/backend-auth`, `feat/frontend-orders`, etc.
- Ao fim do grupo, a branch vira um PR para a `main`. O merge é feito **sem squash**, para preservar o histórico incremental.

### Commits

- Um commit por tarefa ou grupo coeso de tarefas, nunca um commit único no final.
- Mensagens no padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/): `chore:`, `feat(backend):`, `test(backend):`, `feat(frontend):`, `docs:`, `ci:`.
- O corpo da mensagem cita as tarefas atendidas, por exemplo `Refs: tasks 5.4`.

### Revisão manual

- Todo commit e todo push passam por revisão manual do autor antes de serem feitos, inclusive quando o código é produzido com apoio de IA.
- Depois que o CI (GitHub Actions, tarefas 8.4 e 9.9) estiver configurado, ele precisa estar verde antes do merge de qualquer PR.

## Front-end

React 19 + TypeScript (`strict`) + Vite, em `frontend/`. Estado em três camadas: servidor (TanStack Query), sessão (Zustand em `sessionStorage`, encerrada ao fechar a aba ou quando o token expira) e formulários (React Hook Form + Zod, nas próximas telas).

```bash
cd frontend
npm ci
npm run dev             # http://localhost:5173; o Vite repassa /api para http://localhost:8080
npm run lint            # ESLint com jsx-a11y
npm run test:coverage   # Vitest + Testing Library + MSW + axe; falha abaixo de 70% de cobertura
npm run build
```

Estrutura de `src/`: `app/` (roteador, providers, `AppShell`), `features/` (auth, orders), `shared/` (`api` com `apiClient`/`ApiError`, `session`, `ui`, `a11y`, `lib`) e `test/` (setup, handlers MSW, `renderWithProviders`). Componentes não chamam `fetch`: tudo passa pelo `apiClient`, que injeta o `Authorization`, converte `application/problem+json` em `ApiError` e encerra a sessão num `401`.

Telas de autenticação (`/login` e `/register`): formulários com React Hook Form + Zod, cujos schemas espelham as regras da API (nome 2–100, e-mail válido até 254, senha 8–72 caracteres e 72 bytes, com letra e dígito). Erros de validação ficam junto de cada campo (`aria-invalid` + `aria-describedby`) e num resumo que recebe o foco; `400` do servidor é ligado ao campo pelo `errors[].field`, `409` marca o e-mail e `401` mostra uma mensagem única ("E-mail ou senha inválidos."). Depois do cadastro o login abre com o e-mail preenchido e anuncia "Conta criada"; depois do login o usuário volta à rota que tentou acessar (`?redirect=`, aceitando só caminhos internos).

Acessibilidade por construção: link "Pular para o conteúdo", marcos `header`/`nav`/`main`, um `h1` por tela com título do documento e foco atualizados a cada rota, região `aria-live` global, `Button` com `aria-disabled` no estado pendente, `TextField` com `aria-invalid`/`aria-describedby` e `FormErrorSummary` focado. Os testes rodam o axe nas telas e nos componentes.

## Back-end

API REST em `backend/` (Java + Spring Boot, Maven). Pré-requisito: JDK 25.

### Versões efetivas

| Componente | Versão |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.1 |
| Hibernate ORM (+ community dialects) | 7.4.5.Final |
| Flyway | 12.4.0 |
| sqlite-jdbc | 3.53.2.1 |
| springdoc-openapi | 3.1.1 |
| JaCoCo | 0.8.15 |
| Maven (wrapper) | 3.9.11 |

### Rodando

```bash
cd backend
export FDL_JWT_SECRET="$(openssl rand -base64 48)"   # sem segredo (>= 32 caracteres) a aplicação não sobe
./mvnw spring-boot:run      # sobe em http://localhost:8080 (perfil dev)
./mvnw verify               # testes unitários + integração + cobertura mínima de 80% de linhas
curl http://localhost:8080/actuator/health
```

### Configuração

| Variável | Padrão | Uso |
| --- | --- | --- |
| `FDL_DB_PATH` | `./data/fdl.db` | Arquivo do SQLite (o diretório é criado no primeiro start) |
| `FDL_JWT_SECRET` | vazio | Segredo HS256 (>= 256 bits); **obrigatório** fora do perfil `test`: sem ele (ou com menos de 32 caracteres) a aplicação não sobe |
| `FDL_JWT_TTL` | `PT60M` | Validade do token |
| `FDL_BCRYPT_STRENGTH` | `12` | Custo do BCrypt (4–15) |
| `FDL_CORS_ORIGINS` | `http://localhost:5173` | Origens permitidas |
| `FDL_SWAGGER_ENABLED` | `true` | Liga/desliga OpenAPI e Swagger UI |

Perfis: `dev` (padrão) e `test` (usado nos testes, com segredo JWT fixo).

### Banco de dados

SQLite em arquivo (`FDL_DB_PATH`), com o schema criado por migrações Flyway em `backend/src/main/resources/db/migration` (`V1__create_users.sql`, `V2__create_orders.sql`) e `ddl-auto=validate`. A conexão usa `journal_mode=WAL`, `foreign_keys=ON` e `busy_timeout=5000`, com pool de 1 conexão, porque o SQLite aceita um escritor por vez.

Tabelas: `users`, `orders`, `order_items` e `order_status_history` (`V3` acrescenta a coluna `position`, que preserva a ordem de itens e histórico). Os valores de `status` e as regras de quantidade e preço são garantidos também por `CHECK` no banco.

### Domínio de pedidos

O agregado `Order` (`com.fdl.platform.order.domain`) concentra as regras de negócio, sem depender de Spring: total calculado no servidor, status inicial `RECEBIDO` e histórico de status. As transições vivem em `OrderStatus`:

| De | Para (permitido) |
| --- | --- |
| `RECEBIDO` | `EM_PREPARO`, `CANCELADO` |
| `EM_PREPARO` | `SAIU_PARA_ENTREGA`, `CANCELADO` |
| `SAIU_PARA_ENTREGA` | `ENTREGUE` |
| `ENTREGUE`, `CANCELADO` | nenhuma (estados finais) |

Qualquer outra transição lança `InvalidStatusTransitionException`, que a API traduzirá em `409`.

### Testes e CI

`./mvnw verify` roda os testes unitários (Surefire) e de integração (`*IT`, Failsafe, com SQLite em arquivo temporário) e falha se a cobertura de linhas ficar abaixo de 80% (hoje ~96%). `OrderFlowIT` percorre a jornada completa pela API (cadastro, login, criar, listar, buscar, avançar até `ENTREGUE` e a tentativa de cancelar) e `PersistenceRestartIT` sobe a aplicação duas vezes sobre o mesmo arquivo para provar a persistência.

O workflow `.github/workflows/ci.yml` roda em todo PR e em todo push na `main`: o job `backend` (Temurin 25, cache do Maven, `./mvnw verify`) e o job `frontend` (Node 22, `npm ci`, `lint`, `test:coverage`, `build`), publicando os relatórios como artefato.

### Autenticação

Cadastro e login são públicos; todo o resto da API (exceto `/actuator/health` e a documentação OpenAPI) exige `Authorization: Bearer <token>`. O token é um JWT HS256 com `sub` (id do usuário), `email`, `name`, `iss`, `iat` e `exp`.

| Endpoint | Resposta |
| --- | --- |
| `POST /api/v1/auth/register` (`name`, `email`, `password`) | `201` com `id`, `name`, `email` (em minúsculas); `409 EMAIL_ALREADY_REGISTERED` se o e-mail existir, sem diferenciar maiúsculas |
| `POST /api/v1/auth/login` (`email`, `password`) | `200` com `accessToken`, `tokenType` = `Bearer`, `expiresIn` (segundos); `401 INVALID_CREDENTIALS` igual para senha errada e e-mail inexistente |
| `GET /api/v1/auth/me` | `200` com `id`, `name`, `email` do dono do token |

A senha precisa ter 8–72 caracteres (e no máximo 72 bytes em UTF-8, limite do BCrypt) com ao menos uma letra e um dígito, e só é guardada como hash BCrypt. Senhas e tokens não aparecem em logs: os DTOs mascaram esses campos no `toString`, e um teste de integração captura os logs em DEBUG para garantir isso. A documentação interativa está em `/swagger-ui.html` (`/v3/api-docs`).

### Pedidos

Todos os endpoints exigem `Authorization: Bearer <token>`.

| Endpoint | Resposta |
| --- | --- |
| `POST /api/v1/orders` (`customer`, `items`, `deliveryAddress`) | `201` com `Location: /api/v1/orders/{id}`, status `RECEBIDO` e `totalCents` calculado pelo servidor |
| `GET /api/v1/orders?status&page&size&sort` | `200` com `{content, page, size, totalElements, totalPages}`; padrão `page=0`, `size=100` (máximo 500), `sort=createdAt,desc` |
| `GET /api/v1/orders/{id}` | `200` com itens, endereço, `history` e `allowedTransitions`; `404 ORDER_NOT_FOUND` |
| `PATCH /api/v1/orders/{id}/status` (`status`) | `200` com o pedido; `409 INVALID_STATUS_TRANSITION` ou `409 CONCURRENT_MODIFICATION` |

Regras do payload de criação: `customer.name` 2–100 caracteres, `customer.phone` 10–11 dígitos, 1–50 itens, `quantity` inteira de 1 a 99, `unitPriceCents` de 0 a 10.000.000, `state` com 2 letras maiúsculas, `zipCode` com 8 dígitos e `complement` opcional. `id`, `status`, `totalCents` e qualquer campo desconhecido resultam em `400 MALFORMED_REQUEST`. A ordenação aceita `createdAt`, `updatedAt`, `status` e `totalCents`.

Transições de status permitidas:

| De | Para |
| --- | --- |
| `RECEBIDO` | `EM_PREPARO`, `CANCELADO` |
| `EM_PREPARO` | `SAIU_PARA_ENTREGA`, `CANCELADO` |
| `SAIU_PARA_ENTREGA` | `ENTREGUE` |
| `ENTREGUE`, `CANCELADO` | nenhuma (estados finais) |

Cada transição aceita grava uma entrada no histórico (origem, destino, usuário e data/hora UTC); transições rejeitadas não gravam. Duas atualizações do mesmo pedido a partir da mesma versão aplicam no máximo uma (`@Version`); a outra recebe `409 CONCURRENT_MODIFICATION`.

### Erros

Toda resposta de erro usa `Content-Type: application/problem+json` (RFC 9457), com `type`, `title`, `status`, `detail`, `instance`, um `code` estável e o `traceId` da requisição (o mesmo valor do cabeçalho `X-Trace-Id` e dos logs). Erros de validação trazem `errors[]` com `field` e `message`.

```json
{
  "type": "urn:fdl:problem:validation-error",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos são inválidos.",
  "instance": "/api/v1/orders",
  "code": "VALIDATION_ERROR",
  "errors": [{ "field": "items[0].quantity", "message": "must be less than or equal to 99" }],
  "traceId": "7c1e2f9a-5d0b-4c3a-9a55-0e8f2b6d1c44"
}
```

| Status | `code` | Quando |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Bean Validation em corpo ou parâmetros |
| 400 | `MALFORMED_REQUEST` | JSON inválido, campo desconhecido, tipo errado (`1.5`, `"dois"`), enum inválido |
| 400 | `INVALID_PARAMETER` | UUID inválido no caminho, parâmetro ausente ou de tipo errado |
| 401 | `INVALID_CREDENTIALS` / `UNAUTHENTICATED` | Login incorreto / sem token ou token inválido |
| 404 | `ORDER_NOT_FOUND` / `NOT_FOUND` | Pedido ou rota inexistente |
| 409 | `INVALID_STATUS_TRANSITION` | Inclui `currentStatus`, `requestedStatus` e `allowedTransitions` |
| 409 | `CONCURRENT_MODIFICATION` / `EMAIL_ALREADY_REGISTERED` | Versão desatualizada / e-mail repetido |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` diferente de JSON |
| 500 | `INTERNAL_ERROR` | Sem mensagem da exceção nem pilha; o detalhe fica só no log, com o `traceId` |

O Jackson 3 é configurado de forma estrita em `JacksonConfig`: rejeita campos desconhecidos, não converte `1.5` nem `"2"` em inteiro, não lê enum em minúsculas nem por número e rejeita `null` em tipos primitivos.
