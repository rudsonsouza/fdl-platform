import { screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { API, problem } from '../../../test/msw/handlers'
import { server } from '../../../test/msw/server'
import { renderApp } from '../../../test/renderWithProviders'

const name = () => screen.getByRole('textbox', { name: /Nome/ })
const email = () => screen.getByRole('textbox', { name: /E-mail/ })
const password = () => screen.getByLabelText(/Senha/)
const submit = () => screen.getByRole('button', { name: /^Criar conta/ })

async function openRegister(route = '/register') {
  const rendered = renderApp({ route })
  await screen.findByRole('heading', { level: 1, name: 'Criar conta' })
  return rendered
}

async function fill(
  user: Awaited<ReturnType<typeof openRegister>>['user'],
  values: { name?: string; email?: string; password?: string },
) {
  if (values.name) await user.type(name(), values.name)
  if (values.email) await user.type(email(), values.email)
  if (values.password) await user.type(password(), values.password)
}

describe('RegisterPage', () => {
  it('tem rótulos, autocomplete correto e a dica da senha ligada ao campo', async () => {
    await openRegister()

    expect(name()).toHaveAttribute('autocomplete', 'name')
    expect(email()).toHaveAttribute('autocomplete', 'email')
    expect(password()).toHaveAttribute('autocomplete', 'new-password')
    expect(password()).toHaveAccessibleDescription(
      'De 8 a 72 caracteres, com ao menos uma letra e um dígito.',
    )
  })

  it('cadastro bem-sucedido abre o login com o e-mail preenchido e anuncia a conta criada', async () => {
    const { user, router } = await openRegister()

    await fill(user, { name: 'Ana Souza', email: 'ANA.Nova@Exemplo.com', password: 'segredo123' })
    await user.click(submit())

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(email()).toHaveValue('ana.nova@exemplo.com')
    expect(screen.getByText('Conta criada. Entre com seu e-mail e senha.')).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('Conta criada')
    })
  })

  it('o destino original sobrevive ao cadastro', async () => {
    const { user, router } = await openRegister('/register?redirect=/orders/new')

    await fill(user, { name: 'Ana Souza', email: 'nova@exemplo.com', password: 'segredo123' })
    await user.click(submit())

    await screen.findByRole('heading', { level: 1, name: 'Entrar' })
    expect(router.state.location.search).toBe('?redirect=/orders/new')
  })

  it('envio vazio lista nome, e-mail e senha no resumo focado', async () => {
    const { user } = await openRegister()

    await user.click(submit())

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveFocus()
    expect(summary).toHaveTextContent('Nome: é obrigatório')
    expect(summary).toHaveTextContent('E-mail: é obrigatório')
    expect(summary).toHaveTextContent('Senha: é obrigatória')
  })

  it.each([
    [
      'nome em branco',
      { name: '   ', email: 'a@b.com', password: 'segredo123' },
      /Nome/,
      'é obrigatório',
    ],
    [
      'nome curto',
      { name: 'A', email: 'a@b.com', password: 'segredo123' },
      /Nome/,
      'deve ter entre 2 e 100 caracteres',
    ],
    [
      'e-mail inválido',
      { name: 'Ana', email: 'abc', password: 'segredo123' },
      /E-mail/,
      'deve ser um e-mail válido',
    ],
    [
      'senha curta',
      { name: 'Ana', email: 'a@b.com', password: '1234567' },
      /Senha/,
      'deve ter entre 8 e 72 caracteres',
    ],
    [
      'senha sem dígito',
      { name: 'Ana', email: 'a@b.com', password: 'abcdefgh' },
      /Senha/,
      'deve conter ao menos uma letra e um dígito',
    ],
    [
      'senha sem letra',
      { name: 'Ana', email: 'a@b.com', password: '12345678' },
      /Senha/,
      'deve conter ao menos uma letra e um dígito',
    ],
  ])(
    '%s: o erro aparece no campo certo e a API não é chamada',
    async (_case, values, label, message) => {
      let calls = 0
      server.use(
        http.post(`${API}/auth/register`, () => {
          calls += 1
          return problem({ status: 500, code: 'INTERNAL_ERROR', title: 'x', detail: 'x' })
        }),
      )
      const { user } = await openRegister()

      await fill(user, values)
      await user.click(submit())

      const field = screen.getByLabelText(label)
      await waitFor(() => {
        expect(field).toHaveAttribute('aria-invalid', 'true')
      })
      expect(field).toHaveAccessibleDescription(expect.stringContaining(message))
      expect(calls).toBe(0)
    },
  )

  it('e-mail já cadastrado (409): erro junto ao campo, que fica inválido, e resumo focado', async () => {
    const { user } = await openRegister()

    await fill(user, { name: 'Ana', email: 'existente@exemplo.com', password: 'segredo123' })
    await user.click(submit())

    await waitFor(() => {
      expect(email()).toHaveAttribute('aria-invalid', 'true')
    })
    expect(email()).toHaveAccessibleDescription('já está cadastrado')
    expect(await screen.findByRole('alert')).toHaveFocus()
    expect(screen.getByRole('alert')).toHaveTextContent('E-mail: já está cadastrado')
    expect(screen.getByRole('heading', { level: 1, name: 'Criar conta' })).toBeInTheDocument()
  })

  it('o 409 vale também quando o e-mail é digitado em maiúsculas', async () => {
    const { user } = await openRegister()

    await fill(user, { name: 'Ana', email: 'EXISTENTE@exemplo.com', password: 'segredo123' })
    await user.click(submit())

    await waitFor(() => {
      expect(email()).toHaveAttribute('aria-invalid', 'true')
    })
  })

  it('erro 400 do servidor é ligado ao campo informado', async () => {
    server.use(
      http.post(`${API}/auth/register`, () =>
        problem({
          status: 400,
          code: 'VALIDATION_ERROR',
          title: 'Dados inválidos',
          detail: 'Um ou mais campos são inválidos.',
          errors: [{ field: 'password', message: 'deve conter ao menos uma letra e um dígito' }],
        }),
      ),
    )
    const { user } = await openRegister()

    await fill(user, { name: 'Ana', email: 'nova@exemplo.com', password: 'segredo123' })
    await user.click(submit())

    await waitFor(() => {
      expect(password()).toHaveAttribute('aria-invalid', 'true')
    })
    expect(password()).toHaveAccessibleDescription(
      expect.stringContaining('deve conter ao menos uma letra e um dígito'),
    )
    expect(await screen.findByRole('alert')).toHaveFocus()
  })

  it('erro inesperado do servidor aparece como alerta geral', async () => {
    server.use(
      http.post(`${API}/auth/register`, () =>
        problem({
          status: 500,
          code: 'INTERNAL_ERROR',
          title: 'Erro interno',
          detail: 'Erro interno. Informe o código de rastreio ao suporte.',
        }),
      ),
    )
    const { user } = await openRegister()

    await fill(user, { name: 'Ana', email: 'nova@exemplo.com', password: 'segredo123' })
    await user.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent('Erro interno. Informe o código')
    expect(name()).not.toHaveAttribute('aria-invalid')
  })

  it('só com o teclado: Tab entre os campos e Enter no botão', async () => {
    const { user } = await openRegister()
    name().focus()

    await user.keyboard('Ana Souza')
    await user.tab()
    await user.keyboard('nova@exemplo.com')
    await user.tab()
    await user.keyboard('segredo123')
    await user.tab()
    expect(submit()).toHaveFocus()
    await user.keyboard('{Enter}')

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument()
  })

  it('o link "Entrar" leva ao login', async () => {
    const { user } = await openRegister()

    await user.click(within(screen.getByRole('main')).getByRole('link', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument()
  })

  it('não tem violações do axe: inicial, com erros de validação e com erro de e-mail repetido', async () => {
    const { user, container } = await openRegister()
    expect(await axe(container)).toHaveNoViolations()

    await user.click(submit())
    await screen.findByRole('alert')
    expect(await axe(container)).toHaveNoViolations()

    await user.type(name(), 'Ana')
    await user.type(email(), 'existente@exemplo.com')
    await user.type(password(), 'segredo123')
    await user.click(submit())
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('E-mail: já está cadastrado')
    })
    expect(await axe(container)).toHaveNoViolations()
  })
})
