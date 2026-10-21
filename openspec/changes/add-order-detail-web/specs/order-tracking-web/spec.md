# Spec Delta

## ADDED Requirements

### Requirement: Detalhe do pedido
A aplicação SHALL oferecer a tela `/orders/:id`, acessível a partir de cada linha da lista, mostrando cliente, endereço, itens, total, status atual e linha do tempo do histórico. Pedido inexistente MUST exibir mensagem de "pedido não encontrado" com link para a lista.

#### Scenario: Abrir detalhe pela lista
- **WHEN** o usuário ativa o link de um pedido na lista
- **THEN** a aplicação abre `/orders/{id}` com os dados e o histórico do pedido

#### Scenario: Pedido inexistente
- **WHEN** a API responde `404` para o pedido
- **THEN** a aplicação exibe "Pedido não encontrado" e um link para voltar à lista

### Requirement: Troca de status na interface
O detalhe SHALL oferecer apenas as ações de status presentes em `allowedTransitions`. Cancelar MUST exigir confirmação. Após sucesso, a aplicação MUST anunciar o novo status e atualizar detalhe e lista; após `409`, MUST anunciar que o pedido mudou, recarregar os dados e exibir as novas ações.

#### Scenario: Ações restritas
- **WHEN** o pedido está em `SAIU_PARA_ENTREGA`
- **THEN** a única ação de status oferecida é "Marcar como entregue"

#### Scenario: Estado final sem ações
- **WHEN** o pedido está em `ENTREGUE` ou `CANCELADO`
- **THEN** nenhuma ação de status é oferecida

#### Scenario: Mudança anunciada
- **WHEN** o usuário altera o status para `EM_PREPARO` com sucesso
- **THEN** uma região `aria-live="polite"` anuncia "Status alterado para Em preparo" e a linha do tempo ganha uma entrada

#### Scenario: Conflito de status
- **WHEN** a API responde `409` a uma mudança de status
- **THEN** a aplicação anuncia que o pedido foi alterado, recarrega os dados e exibe as novas ações disponíveis

### Requirement: Atualização periódica do detalhe
Enquanto a tela de detalhe estiver aberta e visível, a aplicação MUST recarregar o pedido em intervalo de no máximo 15 segundos e pausar quando a aba estiver oculta.

#### Scenario: Mudança feita por outro operador
- **WHEN** outro operador altera o status do pedido aberto
- **THEN** em até 15 segundos o detalhe mostra o novo status e as novas ações

### Requirement: Linha do tempo acessível
A linha do tempo MUST ser uma lista ordenada com data/hora em elemento `time` e a etapa atual marcada com `aria-current="step"`.

#### Scenario: Etapa atual identificada
- **WHEN** o pedido está em `EM_PREPARO`
- **THEN** o item "Em preparo" da linha do tempo tem `aria-current="step"`

### Requirement: Diálogo de confirmação acessível
O diálogo de cancelamento MUST ter `role="alertdialog"`, `aria-modal="true"`, título e descrição associados, manter o foco preso enquanto aberto, fechar com Esc sem cancelar o pedido e devolver o foco ao controle que o abriu.

#### Scenario: Diálogo por teclado
- **WHEN** o usuário abre o diálogo de cancelamento e pressiona Esc
- **THEN** o diálogo fecha sem cancelar o pedido e o foco volta ao botão "Cancelar pedido"

#### Scenario: Confirmação de cancelamento
- **WHEN** o usuário confirma o cancelamento no diálogo
- **THEN** o pedido passa para `CANCELADO` e a mudança é anunciada

### Requirement: Detalhe sem violações automáticas de acessibilidade
A tela de detalhe, a linha do tempo, as ações de status e o diálogo MUST passar na verificação do axe-core sem violações, inclusive nos estados de carregamento, erro e diálogo aberto.

#### Scenario: Verificação automática do detalhe
- **WHEN** o detalhe é renderizado nos testes automatizados com o diálogo aberto
- **THEN** a análise do axe-core não reporta violações
