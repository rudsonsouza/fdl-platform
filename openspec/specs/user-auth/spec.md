# user-auth Specification

## Purpose
Garante que apenas operadores identificados acessem a plataforma, permitindo cadastro com nome, e-mail e senha e login por e-mail e senha com emissão de token de acesso.

## Requirements

### Requirement: Cadastro de usuário
O sistema SHALL permitir o cadastro de um usuário informando nome, e-mail e senha, sem exigir autenticação prévia. O e-mail MUST ser único (comparação sem diferenciar maiúsculas/minúsculas) e a senha MUST ser armazenada apenas como hash irreversível. A resposta MUST NOT conter a senha nem o hash.

#### Scenario: Cadastro bem-sucedido
- **WHEN** um visitante envia `POST /api/v1/auth/register` com nome "Ana Souza", e-mail "ana@exemplo.com" e senha "segredo123"
- **THEN** o sistema responde `201 Created` com `id`, `name` e `email` (em minúsculas) e sem campo de senha

#### Scenario: E-mail já cadastrado
- **WHEN** um visitante tenta cadastrar "ANA@exemplo.com" e "ana@exemplo.com" já existe
- **THEN** o sistema responde `409 Conflict` com `code` `EMAIL_ALREADY_REGISTERED`

#### Scenario: Senha não é armazenada em texto puro
- **WHEN** um usuário é cadastrado com a senha "segredo123"
- **THEN** o valor persistido para a senha é diferente de "segredo123" e é aceito pela verificação de hash

### Requirement: Validação do payload de cadastro
O sistema MUST rejeitar com `400 Bad Request` todo payload de cadastro inválido, sem criar usuário. Regras: `name` obrigatório com 2–100 caracteres não brancos; `email` obrigatório, formato válido, até 254 caracteres; `password` obrigatória com 8–72 caracteres, contendo ao menos uma letra e um dígito; campos desconhecidos não são aceitos.

#### Scenario: Corpo vazio
- **WHEN** o visitante envia `POST /api/v1/auth/register` com `{}`
- **THEN** o sistema responde `400` listando em `errors` os campos `name`, `email` e `password`

#### Scenario: E-mail com formato inválido
- **WHEN** o visitante envia `email` igual a "abc"
- **THEN** o sistema responde `400` com `errors[].field` = `email`

#### Scenario: Senha fraca ou fora do tamanho
- **WHEN** o visitante envia `password` igual a "1234567", "abcdefgh", "12345678" ou com 73 caracteres
- **THEN** o sistema responde `400` com `errors[].field` = `password`

#### Scenario: Nome em branco ou longo demais
- **WHEN** o visitante envia `name` igual a "   " ou com 101 caracteres
- **THEN** o sistema responde `400` com `errors[].field` = `name`

#### Scenario: Campo desconhecido
- **WHEN** o visitante envia um payload válido acrescido de `"role": "ADMIN"`
- **THEN** o sistema responde `400` com `code` `MALFORMED_REQUEST` e nenhum usuário é criado

#### Scenario: JSON malformado
- **WHEN** o visitante envia um corpo que não é JSON válido
- **THEN** o sistema responde `400` com `code` `MALFORMED_REQUEST`

### Requirement: Login com e-mail e senha
O sistema SHALL autenticar um usuário por e-mail e senha e, em caso de sucesso, emitir um token de acesso Bearer com validade limitada (padrão 60 minutos). Credenciais inválidas MUST receber a mesma resposta genérica, sem revelar se o e-mail existe.

#### Scenario: Login bem-sucedido
- **WHEN** um usuário cadastrado envia `POST /api/v1/auth/login` com e-mail e senha corretos
- **THEN** o sistema responde `200 OK` com `accessToken`, `tokenType` = "Bearer" e `expiresIn` em segundos

#### Scenario: Senha incorreta
- **WHEN** o usuário envia a senha errada
- **THEN** o sistema responde `401 Unauthorized` com `code` `INVALID_CREDENTIALS`

#### Scenario: E-mail inexistente
- **WHEN** alguém envia um e-mail não cadastrado
- **THEN** o sistema responde `401 Unauthorized` com a mesma mensagem e `code` do cenário de senha incorreta

### Requirement: Validação do payload de login
O sistema MUST rejeitar com `400 Bad Request` o payload de login com `email` ausente ou inválido, `password` ausente ou vazia, ou JSON malformado, sem tentar autenticar.

#### Scenario: Senha ausente
- **WHEN** o visitante envia `POST /api/v1/auth/login` com apenas `email`
- **THEN** o sistema responde `400` com `errors[].field` = `password`

#### Scenario: E-mail inválido
- **WHEN** o visitante envia `email` igual a "ana@"
- **THEN** o sistema responde `400` com `errors[].field` = `email`

### Requirement: Acesso restrito a usuários autenticados
Todos os endpoints da API, exceto cadastro, login e verificação de saúde, MUST exigir um token de acesso válido no cabeçalho `Authorization: Bearer`. Requisições sem token, com token expirado ou com assinatura inválida MUST receber `401 Unauthorized` no formato de erro padronizado.

#### Scenario: Requisição sem token
- **WHEN** um cliente chama `GET /api/v1/orders` sem cabeçalho `Authorization`
- **THEN** o sistema responde `401` com `code` `UNAUTHENTICATED`

#### Scenario: Token expirado
- **WHEN** um cliente chama `GET /api/v1/orders` com um token cuja validade terminou
- **THEN** o sistema responde `401` com `code` `UNAUTHENTICATED`

#### Scenario: Token adulterado
- **WHEN** um cliente envia um token com assinatura inválida
- **THEN** o sistema responde `401` com `code` `UNAUTHENTICATED`

#### Scenario: Endpoints públicos
- **WHEN** um visitante sem token chama `POST /api/v1/auth/register` ou `POST /api/v1/auth/login`
- **THEN** a requisição é processada normalmente

### Requirement: Consulta do usuário autenticado
O sistema SHALL expor `GET /api/v1/auth/me`, que retorna `id`, `name` e `email` do usuário dono do token.

#### Scenario: Dados do usuário logado
- **WHEN** um usuário autenticado chama `GET /api/v1/auth/me`
- **THEN** o sistema responde `200` com seus `id`, `name` e `email`

### Requirement: Proteção de credenciais
Senhas e tokens MUST NOT aparecer em logs nem em respostas de erro.

#### Scenario: Falha de login não vaza senha
- **WHEN** um login falha com a senha "segredo123"
- **THEN** nem a resposta nem os logs da aplicação contêm "segredo123"
