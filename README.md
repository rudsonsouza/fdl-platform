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

Tabelas: `users`, `orders`, `order_items` e `order_status_history`. Os valores de `status` e as regras de quantidade e preço são garantidos também por `CHECK` no banco.
