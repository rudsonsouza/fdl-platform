import { act, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { apiClient } from '../../../shared/api/apiClient'
import { API, problem } from '../../../test/msw/handlers'
import { server } from '../../../test/msw/server'
import { renderApp } from '../../../test/renderWithProviders'
import { signIn } from '../../../test/token'

async function openList(route = '/orders') {
  signIn()
  const rendered = renderApp({ route })
  await screen.findByRole('heading', { level: 1, name: 'Pedidos' })
  return rendered
}

function row(index: number) {
  const found = rows()[index]
  if (!found) throw new Error(`linha ${index} não encontrada`)
  return found
}

const rows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1)

function pagedResponse(page: number, totalPages: number) {
  return http.get(`${API}/orders`, () =>
    HttpResponse.json({
      content: [
        {
          id: `00000000-0000-0000-0000-00000000000${page}`,
          customerName: `Cliente página ${page + 1}`,
          status: 'RECEBIDO',
          totalItems: 1,
          totalCents: 1000,
          createdAt: '2026-01-02T10:00:00Z',
          updatedAt: '2026-01-02T10:00:00Z',
        },
      ],
      page,
      size: 100,
      totalElements: totalPages * 100,
      totalPages,
    }),
  )
}

describe('OrderListPage', () => {
  it('mostra carregando (aria-busy) e depois a tabela com o status atual de cada pedido', async () => {
    const { container } = await openList()
    const region = container.querySelector('section')
    expect(region).toHaveAttribute('aria-busy', 'true')

    const table = await screen.findByRole('table')

    expect(region).toHaveAttribute('aria-busy', 'false')
    expect(within(table).getByText('Recebido')).toBeVisible()
    expect(within(table).getByText('Em preparo')).toBeVisible()
  })

  it('tabela com caption, cabeçalhos com scope, cliente como cabeçalho de linha e valores formatados', async () => {
    await openList()

    const table = await screen.findByRole('table', {
      name: 'Pedidos, do mais recente ao mais antigo',
    })

    const headers = within(table).getAllByRole('columnheader')
    expect(headers.map((h) => h.textContent)).toEqual([
      'Cliente',
      'Itens',
      'Total',
      'Status',
      'Criado em',
    ])
    for (const header of headers) expect(header).toHaveAttribute('scope', 'col')
    const first = row(0)
    const second = row(1)
    expect(within(first).getByRole('rowheader', { name: 'Maria Silva' })).toHaveAttribute(
      'scope',
      'row',
    )
    expect(within(first).getByText('2')).toBeInTheDocument()
    expect(within(first).getByText(/R\$\s78,90/)).toBeInTheDocument()
    expect(within(first).getByText('02/01/2026', { exact: false })).toBeInTheDocument()
    expect(within(second).getByRole('rowheader', { name: 'João Pereira' })).toBeInTheDocument()
    expect(first.querySelector('time')).toHaveAttribute('datetime', '2026-01-02T10:00:00Z')
  })

  it('o status aparece em texto, nunca só pela cor', async () => {
    await openList()
    await screen.findByRole('table')

    for (const label of ['Recebido', 'Em preparo']) {
      expect(screen.getByText(label, { selector: 'span' })).toHaveTextContent(label)
    }
  })

  it('o filtro por status vai para a URL e filtra a lista', async () => {
    const { user, router } = await openList()
    await screen.findByRole('table')

    await user.selectOptions(screen.getByLabelText('Filtrar por status'), 'EM_PREPARO')

    await waitFor(() => {
      expect(rows()).toHaveLength(1)
    })
    expect(router.state.location.search).toBe('?status=EM_PREPARO')
    expect(within(row(0)).getByText('Em preparo')).toBeVisible()
    expect(screen.getByRole('table', { name: /status Em preparo/ })).toBeInTheDocument()
  })

  it('recarregar a página com o filtro na URL mantém o mesmo resultado', async () => {
    await openList('/orders?status=EM_PREPARO')

    await waitFor(() => {
      expect(rows()).toHaveLength(1)
    })
    expect(screen.getByLabelText('Filtrar por status')).toHaveValue('EM_PREPARO')
    expect(screen.getByText('João Pereira')).toBeInTheDocument()
    expect(screen.queryByText('Maria Silva')).not.toBeInTheDocument()
  })

  it('voltar para "Todos" limpa o filtro da URL', async () => {
    const { user, router } = await openList('/orders?status=EM_PREPARO')
    await screen.findByRole('table')

    await user.selectOptions(screen.getByLabelText('Filtrar por status'), '')

    await waitFor(() => {
      expect(rows()).toHaveLength(2)
    })
    expect(router.state.location.search).toBe('')
  })

  it('ignora status e página inválidos na URL', async () => {
    await openList('/orders?status=PRONTO&page=-3')

    await screen.findByRole('table')
    expect(rows()).toHaveLength(2)
    expect(screen.getByLabelText('Filtrar por status')).toHaveValue('')
  })

  it('lista vazia: mensagem e atalho para criar pedido', async () => {
    server.use(
      http.get(`${API}/orders`, () =>
        HttpResponse.json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
      ),
    )
    await openList()

    expect(await screen.findByText('Nenhum pedido registrado ainda.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Criar um pedido' })).toHaveAttribute(
      'href',
      '/orders/new',
    )
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('lista vazia com filtro cita o status escolhido', async () => {
    await openList('/orders?status=ENTREGUE')

    expect(await screen.findByText('Nenhum pedido com o status "Entregue".')).toBeVisible()
  })

  it('erro: alerta com a mensagem e "Tentar novamente" recarrega', async () => {
    let fail = true
    server.use(
      http.get(`${API}/orders`, () =>
        fail
          ? problem({
              status: 500,
              code: 'INTERNAL_ERROR',
              title: 'Erro interno',
              detail: 'Erro interno do servidor.',
            })
          : HttpResponse.json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
      ),
    )
    const { user } = await openList()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Erro interno do servidor.')
    fail = false
    await user.click(within(alert).getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByText('Nenhum pedido registrado ainda.')).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('"Atualizar" recarrega da API e mostra o pedido novo', async () => {
    const { user } = await openList()
    await screen.findByRole('table')
    expect(rows()).toHaveLength(2)
    await apiClient.post('/orders', {
      customer: { name: 'Novo Cliente', phone: '11987654321' },
      items: [{ name: 'Pizza', quantity: 1, unitPriceCents: 1000 }],
      deliveryAddress: { zipCode: '01310100' },
    })

    await user.click(screen.getByRole('button', { name: 'Atualizar' }))

    expect(await screen.findByText('Novo Cliente')).toBeVisible()
    expect(rows()).toHaveLength(3)
  })

  it('ao voltar para a aba, recarrega e reflete o status atual', async () => {
    await openList()
    await screen.findByRole('table')
    expect(screen.getAllByText('Em preparo', { selector: 'span' })).toHaveLength(1)
    server.use(
      http.get(`${API}/orders`, () =>
        HttpResponse.json({
          content: [
            {
              id: '33333333-3333-3333-3333-333333333333',
              customerName: 'João Pereira',
              status: 'ENTREGUE',
              totalItems: 1,
              totalCents: 4500,
              createdAt: '2026-01-01T10:00:00Z',
              updatedAt: '2026-01-01T12:00:00Z',
            },
          ],
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
    )

    act(() => {
      window.dispatchEvent(new Event('visibilitychange'))
    })

    expect(await screen.findByText('Entregue', { selector: 'span' })).toBeVisible()
    expect(screen.queryByText('Em preparo', { selector: 'span' })).not.toBeInTheDocument()
  })

  it('paginação: nav rotulada, página atual com aria-current e links que mantêm o filtro', async () => {
    server.use(pagedResponse(1, 3))
    await openList('/orders?status=RECEBIDO&page=1')

    const nav = await screen.findByRole('navigation', { name: 'Paginação dos pedidos' })

    expect(within(nav).getByRole('link', { name: 'Página 2' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(within(nav).getByRole('link', { name: 'Página 1' })).not.toHaveAttribute('aria-current')
    expect(within(nav).getByRole('link', { name: 'Anterior' })).toHaveAttribute(
      'href',
      '/orders?status=RECEBIDO',
    )
    expect(within(nav).getByRole('link', { name: 'Próxima' })).toHaveAttribute(
      'href',
      '/orders?status=RECEBIDO&page=2',
    )
    expect(screen.getByText('300 pedidos · página 2 de 3')).toBeInTheDocument()
  })

  it('navegar pela paginação muda a página na URL', async () => {
    server.use(pagedResponse(0, 3))
    const { user, router } = await openList()
    const nav = await screen.findByRole('navigation', { name: 'Paginação dos pedidos' })
    expect(within(nav).queryByRole('link', { name: 'Anterior' })).not.toBeInTheDocument()

    await user.click(within(nav).getByRole('link', { name: 'Próxima' }))

    await waitFor(() => {
      expect(router.state.location.search).toBe('?page=1')
    })
  })

  it('muitas páginas: mostra reticências entre a primeira, a atual e a última', async () => {
    server.use(pagedResponse(5, 12))
    await openList('/orders?page=5')

    const nav = await screen.findByRole('navigation', { name: 'Paginação dos pedidos' })

    const labels = within(nav)
      .getAllByRole('link')
      .map((link) => link.textContent)
    expect(labels).toEqual(['Anterior', '1', '4', '5', '6', '7', '8', '12', 'Próxima'])
    expect(nav.textContent).toContain('…')
  })

  it('sem paginação quando cabe numa página', async () => {
    await openList()
    await screen.findByRole('table')

    expect(
      screen.queryByRole('navigation', { name: 'Paginação dos pedidos' }),
    ).not.toBeInTheDocument()
  })

  it('tem o atalho "Novo pedido"', async () => {
    const { user, router } = await openList()
    await screen.findByRole('table')

    await user.click(within(screen.getByRole('main')).getByRole('link', { name: 'Novo pedido' }))

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/orders/new')
    })
  })

  it('não tem violações do axe: carregando, lista, vazia, erro e paginada', async () => {
    const { container } = await openList()
    expect(await axe(container)).toHaveNoViolations()
    await screen.findByRole('table')
    expect(await axe(container)).toHaveNoViolations()
  })

  it.each([
    [
      'vazia',
      http.get(`${API}/orders`, () =>
        HttpResponse.json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
      ),
    ],
    [
      'com erro',
      http.get(`${API}/orders`, () =>
        problem({ status: 500, code: 'INTERNAL_ERROR', title: 'Erro', detail: 'Erro interno.' }),
      ),
    ],
    ['paginada', pagedResponse(1, 3)],
  ])('sem violações do axe: %s', async (_name, handler) => {
    server.use(handler)
    const { container } = await openList('/orders?page=1')
    await screen.findByRole('heading', { level: 1, name: 'Pedidos' })
    await waitFor(() => {
      expect(screen.queryByText('Carregando pedidos')).not.toBeInTheDocument()
    })

    expect(await axe(container)).toHaveNoViolations()
  })
})
