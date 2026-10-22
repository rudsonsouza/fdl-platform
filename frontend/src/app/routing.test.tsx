import { screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { apiClient } from '../shared/api/apiClient'
import { useAuthStore } from '../shared/session/useAuthStore'
import { API, unauthenticated } from '../test/msw/handlers'
import { server } from '../test/msw/server'
import { renderApp } from '../test/renderWithProviders'
import { signIn } from '../test/token'

describe('rotas e ProtectedRoute', () => {
  it('sem sessão, /orders redireciona para /login?redirect=/orders', async () => {
    const { router } = renderApp({ route: '/orders' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('?redirect=/orders')
  })

  it('guarda também a query string do destino', async () => {
    const { router } = renderApp({ route: '/orders/new?x=1' })

    await screen.findByRole('heading', { level: 1, name: 'Entrar' })
    expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(
      '/orders/new?x=1',
    )
  })

  it('com sessão, mostra a tela protegida', async () => {
    signIn()

    renderApp({ route: '/orders/new' })

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Novo pedido' }),
    ).toBeInTheDocument()
  })

  it('a raiz leva à lista de pedidos', async () => {
    signIn()

    const { router } = renderApp({ route: '/' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Pedidos' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orders')
  })

  it('login e cadastro são públicos', async () => {
    renderApp({ route: '/register' })

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Criar conta' }),
    ).toBeInTheDocument()
  })

  it('rota desconhecida mostra a página 404 com link de volta', async () => {
    signIn()

    renderApp({ route: '/nada-aqui' })

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Página não encontrada' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar para os pedidos' })).toHaveAttribute(
      'href',
      '/orders',
    )
  })

  it('um 401 da API durante o uso leva ao login e anuncia que a sessão expirou', async () => {
    signIn()
    server.use(http.get(`${API}/orders`, () => unauthenticated()))
    const { router } = renderApp({ route: '/orders' })
    await screen.findByRole('heading', { level: 1, name: 'Pedidos' })

    await apiClient.get('/orders').catch(() => undefined)

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/login')
    })
    expect(router.state.location.search).toBe('?redirect=/orders')
    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('Sua sessão expirou')
    })
    expect(useAuthStore.getState().notice).toBe('expired')
  })

  it('sessão expirada pelo relógio também leva ao login', async () => {
    signIn({ expiresInSeconds: 1 })
    const { router } = renderApp({ route: '/orders' })
    await screen.findByRole('heading', { level: 1, name: 'Pedidos' })

    await waitFor(
      () => {
        expect(router.state.location.pathname).toBe('/login')
      },
      { timeout: 3000 },
    )
  })
})
