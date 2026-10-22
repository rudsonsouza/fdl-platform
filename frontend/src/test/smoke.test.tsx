import { screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { renderApp } from './renderWithProviders'

describe('ambiente de testes', () => {
  it('renderiza a aplicação com roteador, providers e jest-dom', async () => {
    const { container } = renderApp({ route: '/login' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeVisible()
    expect(await axe(container)).toHaveNoViolations()
  })
})
