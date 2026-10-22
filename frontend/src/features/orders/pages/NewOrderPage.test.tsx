import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { API, problem } from '../../../test/msw/handlers'
import { server } from '../../../test/msw/server'
import { renderApp } from '../../../test/renderWithProviders'
import { signIn } from '../../../test/token'

async function openForm() {
  signIn()
  const rendered = renderApp({ route: '/orders/new' })
  await screen.findByRole('heading', { level: 1, name: 'Novo pedido' })
  return rendered
}

type User = Awaited<ReturnType<typeof openForm>>['user']

async function fillValid(user: User, zip = '01310100') {
  await user.type(screen.getByLabelText(/Nome do cliente/), 'Ana Souza')
  await user.type(screen.getByLabelText(/Telefone/), '11987654321')
  await user.type(screen.getByLabelText(/Nome do item/), 'Pizza')
  await user.clear(screen.getByLabelText(/Quantidade/))
  await user.type(screen.getByLabelText(/Quantidade/), '2')
  await user.type(screen.getByLabelText(/Preço unitário/), '12,50')
  await user.type(screen.getByLabelText(/Rua/), 'Av. Paulista')
  await user.type(screen.getByLabelText(/Número/), '1000')
  await user.type(screen.getByLabelText(/Bairro/), 'Bela Vista')
  await user.type(screen.getByLabelText(/Cidade/), 'São Paulo')
  await user.type(screen.getByLabelText(/UF/), 'SP')
  await user.type(screen.getByLabelText(/CEP/), zip)
}

const submit = (user: User) => user.click(screen.getByRole('button', { name: 'Criar pedido' }))

describe('NewOrderPage', () => {
  it('agrupa os campos em fieldsets com legenda', async () => {
    await openForm()

    for (const name of ['Cliente', 'Itens do pedido', 'Item 1', 'Endereço de entrega']) {
      expect(screen.getByRole('group', { name })).toBeInTheDocument()
    }
  })

  it('mostra o total do pedido atualizado ao digitar e ao adicionar itens', async () => {
    const { user } = await openForm()
    expect(screen.getByText(/Total do pedido:/)).toHaveTextContent('R$ 0,00')

    await user.clear(screen.getByLabelText(/Quantidade/))
    await user.type(screen.getByLabelText(/Quantidade/), '2')
    await user.type(screen.getByLabelText(/Preço unitário/), '12')
    await user.click(screen.getByRole('button', { name: 'Adicionar item' }))
    const second = screen.getByRole('group', { name: 'Item 2' })
    await user.type(within(second).getByLabelText(/Preço unitário/), '54,90')

    expect(screen.getByText(/Total do pedido:/).textContent.normalize('NFKC')).toContain('R$ 78,90')
  })

  it('anuncia e move o foco ao adicionar e remover itens', async () => {
    const { user } = await openForm()
    expect(screen.queryByRole('button', { name: /Remover item/ })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Adicionar item' }))
    await waitFor(() => {
      expect(
        within(screen.getByRole('group', { name: 'Item 2' })).getByLabelText(/Nome do item/),
      ).toHaveFocus()
    })
    expect(await screen.findByText('Item 2 adicionado')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Remover item 2' }))
    expect(screen.queryByRole('group', { name: 'Item 2' })).not.toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByLabelText(/Nome do item/)).toHaveFocus()
    })
    expect(await screen.findByText('Item 2 removido')).toBeInTheDocument()
  })

  it('limita o pedido a 50 itens', async () => {
    const { user } = await openForm()
    const add = screen.getByRole('button', { name: 'Adicionar item' })
    for (let i = 0; i < 55; i++) await user.click(add)

    expect(screen.getAllByRole('group', { name: /^Item \d+$/ })).toHaveLength(50)
    expect(screen.getByText(/máximo de 50 itens/)).toBeInTheDocument()
  })

  it('envio vazio mostra o resumo com foco e links que levam aos campos', async () => {
    const { user } = await openForm()

    await submit(user)

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveFocus()
    const link = summary.querySelector('a[href="#f-customer-name"]')
    await user.click(within(summary).getByRole('link', { name: link?.textContent ?? '' }))
    expect(screen.getByLabelText(/Nome do cliente/)).toHaveFocus()
    expect(screen.getByLabelText(/Nome do cliente/)).toHaveAttribute('aria-invalid', 'true')
  })

  it.each([
    ['Telefone', 'abc', /Telefone/],
    ['CEP', '123', /CEP/],
    ['UF', 'sp1', /UF/],
  ])('rejeita %s inválido', async (_label, value, field) => {
    const { user } = await openForm()
    await fillValid(user)
    const input = screen.getByLabelText(field)
    await user.clear(input)
    await user.type(input, value)

    await submit(user)

    await waitFor(() => {
      expect(input).toHaveAttribute('aria-invalid', 'true')
    })
  })

  it('rejeita quantidade zero e preço com mais de duas casas', async () => {
    const { user } = await openForm()
    await fillValid(user)
    const qty = screen.getByLabelText(/Quantidade/)
    await user.clear(qty)
    await user.type(qty, '0')
    const price = screen.getByLabelText(/Preço unitário/)
    await user.clear(price)
    await user.type(price, '1,234')

    await submit(user)

    await waitFor(() => {
      expect(qty).toHaveAttribute('aria-invalid', 'true')
    })
    expect(price).toHaveAttribute('aria-invalid', 'true')
  })

  it('cria o pedido, anuncia e mostra o novo pedido como Recebido na lista', async () => {
    const { user, router } = await openForm()
    await fillValid(user)

    await submit(user)

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/orders')
    })
    expect(await screen.findByText('Ana Souza')).toBeVisible()
    expect(await screen.findByText('Pedido criado')).toBeInTheDocument()
  })

  it('envia centavos e omite o complemento vazio', async () => {
    let body: Record<string, unknown> = {}
    server.use(
      http.post(`${API}/orders`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: crypto.randomUUID() }, { status: 201 })
      }),
    )
    const { user } = await openForm()
    await fillValid(user)

    await submit(user)

    await waitFor(() => {
      expect(body).toHaveProperty('items')
    })
    expect(body.items).toEqual([{ name: 'Pizza', quantity: 2, unitPriceCents: 1250 }])
    expect(body.deliveryAddress).not.toHaveProperty('complement')
  })

  it('mapeia erros 400 do servidor para os campos', async () => {
    const { user } = await openForm()
    await fillValid(user, '00000000x')
    server.use(
      http.post(`${API}/orders`, () =>
        problem({
          status: 400,
          code: 'VALIDATION_ERROR',
          title: 'Dados inválidos',
          detail: 'Um ou mais campos são inválidos.',
          errors: [
            { field: 'deliveryAddress.zipCode', message: 'CEP não existe' },
            { field: 'items[0].unitPriceCents', message: 'preço fora do limite' },
          ],
        }),
      ),
    )
    const zip = screen.getByLabelText(/CEP/)
    await user.clear(zip)
    await user.type(zip, '01310100')

    await submit(user)

    expect(await screen.findByText('CEP não existe')).toBeInTheDocument()
    expect(zip).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText(/Preço unitário/)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('alert')).toHaveFocus()
  })

  it('mostra erro do servidor sem campo correspondente em um alerta', async () => {
    server.use(
      http.post(`${API}/orders`, () =>
        problem({
          status: 500,
          code: 'INTERNAL_ERROR',
          title: 'Erro interno',
          detail: 'Algo deu errado. Tente novamente.',
        }),
      ),
    )
    const { user, router } = await openForm()
    await fillValid(user)

    await submit(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Algo deu errado. Tente novamente.')
    expect(router.state.location.pathname).toBe('/orders/new')
  })

  it('duplo clique em Criar pedido envia uma única requisição', async () => {
    let calls = 0
    server.use(
      http.post(`${API}/orders`, async () => {
        calls++
        await new Promise((resolve) => setTimeout(resolve, 100))
        return HttpResponse.json({ id: crypto.randomUUID() }, { status: 201 })
      }),
    )
    const { user, router } = await openForm()
    await fillValid(user)
    const button = screen.getByRole('button', { name: 'Criar pedido' })

    await user.dblClick(button)

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/orders')
    })
    expect(calls).toBe(1)
  })

  it('permite preencher e enviar só com o teclado', async () => {
    const { user, router } = await openForm()
    await user.tab() // skip link já foi consumido pelo primeiro tab abaixo
    screen.getByLabelText(/Nome do cliente/).focus()
    await user.keyboard('Ana Souza{Tab}11987654321{Tab}Pizza{Tab}2{Tab}12,50')
    screen.getByLabelText(/Rua/).focus()
    await user.keyboard(
      'Av. Paulista{Tab}1000{Tab}{Tab}Bela Vista{Tab}São Paulo{Tab}SP{Tab}01310100{Enter}',
    )

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/orders')
    })
  })

  it('não tem violações de acessibilidade (inicial, com erros e com erro do servidor)', async () => {
    const { user, container } = await openForm()
    expect(await axe(container)).toHaveNoViolations()

    await submit(user)
    await screen.findByRole('alert')
    expect(await axe(container)).toHaveNoViolations()
  })
})
