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
