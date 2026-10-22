# Spec Delta

## Purpose

Aplicação web pela qual operadores se cadastram, fazem login, listam os pedidos com seus status atuais e criam novos pedidos, utilizável integralmente por teclado e por tecnologias assistivas (WAI-ARIA, WCAG 2.2 AA).

## ADDED Requirements

### Requirement: Telas de cadastro e login
A aplicação SHALL oferecer telas de cadastro (nome, e-mail, senha) e login (e-mail, senha), com link entre elas. Após cadastro bem-sucedido, o usuário MUST ser levado ao login com o e-mail preenchido. Após login bem-sucedido, MUST ser levado à rota que tentou acessar ou à lista de pedidos.

#### Scenario: Login redireciona ao destino original
- **WHEN** um visitante acessa `/orders/new`, é enviado ao login e se autentica com sucesso
- **THEN** a aplicação o leva para `/orders/new`

#### Scenario: Cadastro concluído
- **WHEN** um visitante conclui o cadastro com sucesso
- **THEN** a aplicação abre o login com o e-mail preenchido e anuncia que a conta foi criada

#### Scenario: Credenciais inválidas
- **WHEN** o servidor responde `401` ao login
- **THEN** a aplicação exibe uma mensagem genérica de credenciais inválidas anunciada a leitores de tela

#### Scenario: E-mail já cadastrado
- **WHEN** o servidor responde `409` ao cadastro
- **THEN** a mensagem aparece junto ao campo de e-mail, que fica marcado como inválido

### Requirement: Rotas protegidas e sessão
Todas as telas exceto login e cadastro MUST exigir sessão ativa. Ao expirar o token ou receber `401` da API, a aplicação MUST encerrar a sessão, redirecionar ao login e informar o motivo. A aplicação MUST oferecer ação de sair, e a sessão MUST ser descartada ao fechar a aba do navegador.

#### Scenario: Acesso sem sessão
- **WHEN** um visitante sem sessão abre `/orders`
- **THEN** a aplicação redireciona para `/login?redirect=/orders`

#### Scenario: Sessão expirada
- **WHEN** a API responde `401` durante o uso
- **THEN** a aplicação redireciona para o login e anuncia "Sua sessão expirou"

#### Scenario: Sair
- **WHEN** o usuário aciona "Sair"
- **THEN** a sessão é descartada e a aplicação mostra o login

### Requirement: Lista de pedidos com status atual
A aplicação SHALL listar os pedidos em tabela com cliente, quantidade de itens, total (em reais), **status atual** e data de criação, mais recentes primeiro, com filtro por status refletido na URL e estados explícitos de carregamento, lista vazia e erro. Quando houver mais pedidos que o tamanho da página da API, MUST oferecer paginação.

#### Scenario: Pedidos com status atual
- **WHEN** um usuário autenticado abre `/orders` e existem pedidos em `RECEBIDO` e `EM_PREPARO`
- **THEN** cada linha mostra o status atual do pedido em texto ("Recebido", "Em preparo")

#### Scenario: Filtro preservado na URL
- **WHEN** o usuário filtra por `EM_PREPARO`
- **THEN** a URL contém `status=EM_PREPARO` e recarregar a página mantém o mesmo resultado

#### Scenario: Lista vazia
- **WHEN** não há pedidos para o filtro escolhido
- **THEN** a aplicação exibe uma mensagem de lista vazia com atalho para criar pedido

#### Scenario: Atualizar lista
- **WHEN** o usuário aciona "Atualizar" ou volta à aba do navegador
- **THEN** a lista é recarregada da API e reflete os status atuais

### Requirement: Criação de pedido na interface
A aplicação SHALL oferecer formulário com dados do cliente, itens dinâmicos (adicionar/remover, mínimo um) e endereço de entrega, mostrando o total em tempo real. A validação no cliente MUST espelhar as regras da API, e erros `400` retornados pelo servidor MUST ser exibidos junto aos campos correspondentes.

#### Scenario: Pedido criado
- **WHEN** o usuário preenche o formulário corretamente e envia
- **THEN** a aplicação volta para a lista, anuncia "Pedido criado" e o novo pedido aparece com status "Recebido"

#### Scenario: Total em tempo real
- **WHEN** o usuário informa 2 itens de R$ 12,00 e 1 item de R$ 54,90
- **THEN** o total exibido é R$ 78,90

#### Scenario: Erro de validação do servidor
- **WHEN** a API responde `400` com `errors[].field` = `deliveryAddress.zipCode`
- **THEN** a mensagem aparece junto ao campo CEP, que fica marcado como inválido

#### Scenario: Envio duplicado bloqueado
- **WHEN** o usuário aciona "Criar pedido" duas vezes seguidas
- **THEN** apenas uma requisição é enviada e o botão fica indisponível com `aria-disabled` enquanto ela está em andamento

### Requirement: Estrutura e navegação acessíveis
A aplicação MUST usar marcos semânticos (`header`, `nav`, `main`), um link "Pular para o conteúdo" como primeiro elemento focável e um único `h1` por tela. A cada troca de rota, o título do documento MUST ser atualizado e o foco movido para o `h1` da nova tela.

#### Scenario: Troca de rota anunciada
- **WHEN** o usuário navega da lista para o formulário de novo pedido
- **THEN** o título da aba muda para "Novo pedido" e o foco vai para o `h1` da tela

#### Scenario: Pular navegação
- **WHEN** o usuário pressiona Tab ao carregar qualquer tela e ativa o primeiro link
- **THEN** o foco vai para o conteúdo principal

### Requirement: Formulários acessíveis
Todo campo MUST ter rótulo visível associado. Campos inválidos MUST ter `aria-invalid="true"` e `aria-describedby` apontando para a mensagem de erro. Ao enviar com erros, um resumo de erros com links para os campos MUST receber o foco. Grupos de campos (cliente, itens, endereço) MUST usar `fieldset`/`legend`, e adicionar/remover itens MUST ser anunciado.

#### Scenario: Envio com erros
- **WHEN** o usuário envia o formulário de pedido com o CEP vazio
- **THEN** o foco vai para o resumo de erros, que contém um link para o campo CEP, e o campo tem `aria-invalid="true"` ligado à mensagem

#### Scenario: Remoção de item anunciada
- **WHEN** o usuário remove o item 2
- **THEN** uma região `aria-live` anuncia "Item 2 removido" e o foco vai para um controle próximo

### Requirement: Componentes de dados e feedback acessíveis
A tabela de pedidos MUST ter `caption` e cabeçalhos com `scope`; a paginação MUST ser um `nav` rotulado com `aria-current="page"`; o status MUST ser comunicado por texto, nunca só por cor; regiões em carregamento MUST ter `aria-busy`; mensagens de sucesso MUST usar região `aria-live="polite"` e erros `role="alert"`.

#### Scenario: Sucesso anunciado
- **WHEN** um pedido é criado com sucesso
- **THEN** uma região `aria-live="polite"` anuncia "Pedido criado"

#### Scenario: Status sem depender de cor
- **WHEN** a lista é exibida em escala de cinza
- **THEN** o status de cada pedido continua identificável pelo texto

### Requirement: Operação por teclado
Todas as funcionalidades MUST ser operáveis só por teclado, com indicador de foco visível e ordem de foco lógica.

#### Scenario: Criar pedido só com teclado
- **WHEN** o usuário preenche e envia o formulário de novo pedido usando apenas Tab, Shift+Tab, Enter e Espaço
- **THEN** o pedido é criado e o foco sempre permanece visível

### Requirement: Ausência de violações automáticas de acessibilidade
Todas as telas e componentes compartilhados MUST passar na verificação automática do axe-core sem violações, e o contraste de texto MUST atender WCAG AA (4.5:1).

#### Scenario: Verificação automática
- **WHEN** cada tela é renderizada nos testes automatizados (incluindo estados de erro)
- **THEN** a análise do axe-core não reporta violações
