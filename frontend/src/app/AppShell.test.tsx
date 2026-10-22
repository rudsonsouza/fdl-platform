import { screen, waitFor, within } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../shared/session/useAuthStore'
import { renderApp } from '../test/renderWithProviders'
import { signIn } from '../test/token'

describe('AppShell', () => {
  it('usa marcos semânticos e um único h1', async () => {
    signIn()
    renderApp({ route: '/orders' })

    await screen.findByRole('heading', { level: 1, name: 'Pedidos' })

    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Principal' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  })

  it('o skip link é o primeiro elemento focável e leva o foco ao conteúdo principal', async () => {
    signIn()
    const { user } = renderApp({ route: '/orders' })
    await screen.findByRole('heading', { level: 1, name: 'Pedidos' })

    await user.tab()
    const skip = screen.getByRole('link', { name: 'Pular para o conteúdo' })
    expect(skip).toHaveFocus()

    await user.keyboard('{Enter}')
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('ao trocar de rota atualiza o título do documento e foca o h1 da nova tela', async () => {
    signIn()
    const { user } = renderApp({ route: '/orders' })
    await screen.findByRole('heading', { level: 1, name: 'Pedidos' })
    expect(document.title).toBe('Pedidos')

    await user.click(screen.getByRole('link', { name: 'Novo pedido' }))

    const heading = await screen.findByRole('heading', { level: 1, name: 'Novo pedido' })
    await waitFor(() => {
      expect(heading).toHaveFocus()
    })
    expect(document.title).toBe('Novo pedido')
  })

  it('no carregamento inicial ajusta o título sem roubar o foco', async () => {
    signIn()
    renderApp({ route: '/orders' })

    const heading = await screen.findByRole('heading', { level: 1, name: 'Pedidos' })

    expect(document.title).toBe('Pedidos')
    expect(heading).not.toHaveFocus()
  })

  it('logado, mostra o nome e a ação Sair; "Sair" descarta a sessão e abre o login', async () => {
    signIn({ name: 'Ana Souza' })
    const { user, router } = renderApp({ route: '/orders' })
    await screen.findByRole('heading', { level: 1, name: 'Pedidos' })
    expect(within(screen.getByRole('navigation')).getByText('Ana Souza')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Sair' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(useAuthStore.getState().token).toBeNull()
    expect(sessionStorage.getItem('fdl.session') ?? '').not.toContain('Ana Souza')
  })

  it('sem sessão, a navegação oferece Entrar e Criar conta', async () => {
    renderApp({ route: '/register' })
    await screen.findByRole('heading', { level: 1, name: 'Criar conta' })

    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/login')
    expect(within(nav).getByRole('link', { name: 'Criar conta' })).toHaveAttribute(
      'href',
      '/register',
    )
    expect(within(nav).queryByRole('button', { name: 'Sair' })).not.toBeInTheDocument()
  })

  it.each(['/login', '/register'])('não tem violações do axe em %s', async (route) => {
    const { container } = renderApp({ route })
    await screen.findByRole('heading', { level: 1 })

    expect(await axe(container)).toHaveNoViolations()
  })

  it('não tem violações do axe logado', async () => {
    signIn()
    const { container } = renderApp({ route: '/orders' })
    await screen.findByRole('heading', { level: 1, name: 'Pedidos' })

    expect(await axe(container)).toHaveNoViolations()
  })
})
