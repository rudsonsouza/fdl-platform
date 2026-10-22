import { screen, waitFor, within } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../../../shared/session/useAuthStore'
import { API, problem } from '../../../test/msw/handlers'
import { server } from '../../../test/msw/server'
import { renderApp } from '../../../test/renderWithProviders'

const email = () => screen.getByRole('textbox', { name: /E-mail/ })
const password = () => screen.getByLabelText(/Senha/)
const submit = () => screen.getByRole('button', { name: /^Entrar/ })

async function openLogin(route = '/login') {
  const rendered = renderApp({ route })
  await screen.findByRole('heading', { level: 1, name: 'Entrar' })
  return rendered
}

describe('LoginPage', () => {
  it('tem rótulos visíveis, autocomplete correto e link para o cadastro', async () => {
    await openLogin()

    expect(email()).toHaveAttribute('autocomplete', 'email')
    expect(email()).toHaveAttribute('type', 'email')
    expect(password()).toHaveAttribute('autocomplete', 'current-password')
    expect(
      within(screen.getByRole('main')).getByRole('link', { name: 'Criar conta' }),
    ).toBeInTheDocument()
  })

  it('envio vazio: foca o resumo, marca os campos e não chama a API', async () => {
    let calls = 0
    server.use(
      http.post(`${API}/auth/login`, () => {
        calls += 1
        return HttpResponse.json({})
      }),
    )
    const { user } = await openLogin()

    await user.click(submit())

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveFocus()
    expect(summary).toHaveTextContent('E-mail: é obrigatório')
    expect(summary).toHaveTextContent('Senha: é obrigatória')
    expect(email()).toHaveAttribute('aria-invalid', 'true')
    expect(email()).toHaveAccessibleDescription('é obrigatório')
    expect(password()).toHaveAttribute('aria-invalid', 'true')
    expect(calls).toBe(0)
  })

  it('e-mail inválido aponta o campo de e-mail', async () => {
    const { user } = await openLogin()

    await user.type(email(), 'ana@')
    await user.type(password(), 'segredo123')
    await user.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail: deve ser um e-mail válido')
    expect(email()).toHaveAttribute('aria-invalid', 'true')
    expect(password()).not.toHaveAttribute('aria-invalid')
  })

  it('o link do resumo leva o foco ao campo com erro', async () => {
    const { user } = await openLogin()
    await user.click(submit())

    await user.click(await screen.findByRole('link', { name: 'E-mail: é obrigatório' }))

    expect(email()).toHaveFocus()
  })

  it('senha errada: mensagem genérica em role="alert" e senha limpa', async () => {
    const { user } = await openLogin()

    await user.type(email(), 'ana@exemplo.com')
    await user.type(password(), 'errada123')
    await user.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.')
    expect(password()).toHaveValue('')
    expect(email()).toHaveValue('ana@exemplo.com')
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('e-mail inexistente recebe a mesma mensagem da senha errada', async () => {
    const { user } = await openLogin()

    await user.type(email(), 'ninguem@exemplo.com')
    await user.type(password(), 'segredo123')
    await user.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.')
  })

  it('sucesso abre a sessão e vai para a lista de pedidos', async () => {
    const { user, router } = await openLogin()

    await user.type(email(), 'ana@exemplo.com')
    await user.type(password(), 'segredo123')
    await user.click(submit())

    expect(await screen.findByRole('heading', { level: 1, name: 'Pedidos' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orders')
    expect(useAuthStore.getState().user).toMatchObject({
      email: 'ana@exemplo.com',
      name: 'Ana Souza',
    })
  })

  it('volta ao destino original depois do login', async () => {
    const { user, router } = renderApp({ route: '/orders/new' })
    await screen.findByRole('heading', { level: 1, name: 'Entrar' })
    expect(router.state.location.search).toBe('?redirect=/orders/new')

    await user.type(email(), 'ana@exemplo.com')
    await user.type(password(), 'segredo123')
    await user.click(submit())

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Novo pedido' }),
    ).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orders/new')
  })

  it('ignora destinos externos (redirecionamento aberto)', async () => {
    const { user, router } = await openLogin('/login?redirect=//evil.com')

    await user.type(email(), 'ana@exemplo.com')
    await user.type(password(), 'segredo123')
    await user.click(submit())

    expect(await screen.findByRole('heading', { level: 1, name: 'Pedidos' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orders')
  })

  it('erro 400 do servidor aparece junto ao campo', async () => {
    server.use(
      http.post(`${API}/auth/login`, () =>
        problem({
          status: 400,
          code: 'VALIDATION_ERROR',
          title: 'Dados inválidos',
          detail: 'Um ou mais campos são inválidos.',
          errors: [{ field: 'email', message: 'deve ser um e-mail válido' }],
        }),
      ),
    )
    const { user } = await openLogin()

    await user.type(email(), 'ana@exemplo.com')
    await user.type(password(), 'segredo123')
    await user.click(submit())

    expect(await screen.findByRole('alert')).toHaveFocus()
    expect(email()).toHaveAttribute('aria-invalid', 'true')
    expect(email()).toHaveAccessibleDescription('deve ser um e-mail válido')
  })

  it('falha de rede mostra aviso sem expor detalhes técnicos', async () => {
    server.use(http.post(`${API}/auth/login`, () => HttpResponse.error()))
    const { user } = await openLogin()

    await user.type(email(), 'ana@exemplo.com')
    await user.type(password(), 'segredo123')
    await user.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível falar com o servidor',
    )
  })

  it('duplo clique envia uma única requisição e o botão fica aria-disabled', async () => {
    let calls = 0
    server.use(
      http.post(`${API}/auth/login`, async () => {
        calls += 1
        await delay(150)
        return problem({ status: 401, code: 'INVALID_CREDENTIALS', title: 'x', detail: 'x' })
      }),
    )
    const { user } = await openLogin()
    await user.type(email(), 'ana@exemplo.com')
    await user.type(password(), 'segredo123')

    await user.dblClick(submit())

    const pending = screen.getByRole('button', { name: /Entrar/ })
    await waitFor(() => {
      expect(pending).toHaveAttribute('aria-disabled', 'true')
    })
    await screen.findByRole('alert')
    expect(calls).toBe(1)
    expect(submit()).not.toHaveAttribute('aria-disabled', 'true')
  })

  it('mostra que a sessão expirou quando o usuário foi mandado para o login', async () => {
    useAuthStore.getState().logout('expired')

    await openLogin()

    expect(
      screen.getByText('Sua sessão expirou. Entre novamente para continuar.'),
    ).toBeInTheDocument()
  })

  it('funciona só com o teclado: digitar, Tab e Enter', async () => {
    const { user } = await openLogin()
    email().focus()

    await user.keyboard('ana@exemplo.com')
    await user.tab()
    expect(password()).toHaveFocus()
    await user.keyboard('segredo123')
    await user.tab()
    expect(submit()).toHaveFocus()
    await user.keyboard('{Enter}')

    expect(await screen.findByRole('heading', { level: 1, name: 'Pedidos' })).toBeInTheDocument()
  })

  it('Enter dentro de um campo envia o formulário', async () => {
    const { user } = await openLogin()

    await user.type(email(), 'ana@exemplo.com')
    await user.type(password(), 'segredo123{Enter}')

    expect(await screen.findByRole('heading', { level: 1, name: 'Pedidos' })).toBeInTheDocument()
  })

  it('o link para o cadastro mantém o destino', async () => {
    const { user } = await openLogin('/login?redirect=/orders/new')

    await user.click(within(screen.getByRole('main')).getByRole('link', { name: 'Criar conta' }))

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Criar conta' }),
    ).toBeInTheDocument()
    expect(
      within(screen.getByRole('main')).getByRole('link', { name: 'Entrar' }),
    ).toBeInTheDocument()
  })

  it('não tem violações do axe: inicial, com erros de validação e com erro do servidor', async () => {
    const { user, container } = await openLogin()
    expect(await axe(container)).toHaveNoViolations()

    await user.click(submit())
    await screen.findByRole('alert')
    expect(await axe(container)).toHaveNoViolations()

    await user.type(email(), 'ana@exemplo.com')
    await user.type(password(), 'errada123')
    await user.click(submit())
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.')
    })
    expect(await axe(container)).toHaveNoViolations()
  })
})
