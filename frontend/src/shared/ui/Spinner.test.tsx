import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { Spinner } from './Spinner'

describe('Spinner', () => {
  it('o desenho é decorativo e o texto fica disponível a leitores de tela', () => {
    const { container } = render(<Spinner label="Carregando pedidos" />)

    expect(screen.getByText('Carregando pedidos')).toHaveClass('sr-only')
    expect(container.querySelector('.spinner')).toHaveAttribute('aria-hidden', 'true')
  })

  it('tem texto padrão e não tem violações do axe', async () => {
    const { container } = render(<Spinner />)

    expect(screen.getByText('Carregando')).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()
  })
})
