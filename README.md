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
./mvnw spring-boot:run      # sobe em http://localhost:8080 (perfil dev)
./mvnw verify               # testes unitários + integração + cobertura mínima de 80% de linhas
curl http://localhost:8080/actuator/health
```

### Configuração

| Variável | Padrão | Uso |
| --- | --- | --- |
| `FDL_DB_PATH` | `./data/fdl.db` | Arquivo do SQLite (o diretório é criado no primeiro start) |
| `FDL_JWT_SECRET` | vazio | Segredo HS256 (>= 256 bits); obrigatório fora do perfil `test` a partir da tarefa 6.2 |
| `FDL_JWT_TTL` | `PT60M` | Validade do token |
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
