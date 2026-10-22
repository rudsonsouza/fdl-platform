# Revisão de segurança

Feita em 22/10/2026 sobre um clone limpo da `main`, com a pilha rodando (jar + nginx com a mesma configuração do contêiner).

| Verificação | Como | Resultado |
| --- | --- | --- |
| Segredos versionados | `git grep` por `secret/password/token/api_key = "..."`, chaves AWS/GitHub/OpenAI e blocos `PRIVATE KEY` | Nenhum. Só aparecem os exemplos `openssl rand` do README |
| Arquivos sensíveis no Git | `git ls-files` e histórico (`--diff-filter=A`) por `.env`, `*.db`, `*.pem`, `*.key`, `*.patch`, `*.zip` | Só `.env.example` (sem valores) e `frontend/.env.example` |
| `.env` e banco ignorados | `git check-ignore -v .env backend/data/fdl.db` | Ambos ignorados |
| Segredo do JWT | Sem `FDL_JWT_SECRET` (ou com menos de 32 caracteres) a aplicação não sobe; o valor fixo de `application-test.yml` só vale com o perfil `test` | Conforme |
| Senha/token fora dos logs | 124 linhas de log de uma sessão completa (cadastro, login, criação, erros) varridas por senha, JWT, `Bearer`, hash BCrypt e pelo segredo; DTOs mascaram `toString` e `AuthFlowIT.failedLoginLeaksNeitherPasswordNorTokenToTheLogs` cobre em DEBUG | 0 ocorrências; e-mails também não aparecem |
| CORS restrito | `OPTIONS` com `Origin: http://evil.example` e com `http://localhost:5173` | Primeira recusada (`403`), segunda devolve `Access-Control-Allow-Origin: http://localhost:5173`; origem configurável por `FDL_CORS_ORIGINS` |
| Cabeçalhos do front-end | `curl -I` no nginx | CSP restrita à própria origem, `nosniff`, `no-referrer`, `X-Frame-Options: DENY`; a interface roda sem violações de CSP no console |
| Contêineres | Dockerfiles | Back-end e nginx rodam sem root |

## Pontos aceitos e documentados

- O perfil padrão é `dev`, com log `DEBUG` do pacote `com.fdl.platform` (sem dados sensíveis, ver acima). Para um ambiente real, ajustar o perfil/nível de log.
- Token em `sessionStorage` (TTL curto, CSP, sem `dangerouslySetInnerHTML`); evolução: cookie `HttpOnly`.
- Cadastro revela e-mails já existentes (`409`).
- Sem limite de tentativas de login nem refresh token/revogação (fora do escopo do desafio).
