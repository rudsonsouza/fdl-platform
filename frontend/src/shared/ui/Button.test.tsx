import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button'

describe('Button', () => {
  it('tem type="button" por padrão e dispara onClick por mouse e teclado', async () => {
    const onClick = vi.fn()
    const user = userEvent.setup()
    render(<Button onClick={onClick}>Salvar</Button>)
    const button = screen.getByRole('button', { name: 'Salvar' })
    expect(button).toHaveAttribute('type', 'button')

    await user.click(button)
    button.focus()
    await user.keyboard('{Enter}')
    await user.keyboard(' ')

    expect(onClick).toHaveBeenCalledTimes(3)
  })

  it('pendente: aria-disabled, continua focável, mostra o motivo e ignora cliques', async () => {
    const onClick = vi.fn()
    const user = userEvent.setup()
    render(
      <Button pending pendingLabel="Enviando pedido" onClick={onClick}>
        Criar pedido
      </Button>,
    )
    const button = screen.getByRole('button', { name: /Criar pedido/ })

    expect(button).toHaveAttribute('aria-disabled', 'true')
    expect(button).not.toBeDisabled()
    expect(button).toHaveTextContent('Enviando pedido')
    await user.tab()
    expect(button).toHaveFocus()
    await user.click(button)
    await user.keyboard('{Enter}')

    expect(onClick).not.toHaveBeenCalled()
  })

  it('pendente dentro de um formulário bloqueia o envio duplicado', async () => {
    const onSubmit = vi.fn((event: React.SyntheticEvent) => {
      event.preventDefault()
    })
    const user = userEvent.setup()
    render(
      <form onSubmit={onSubmit}>
        <Button type="submit" pending>
          Enviar
        </Button>
      </form>,
    )

    await user.click(screen.getByRole('button', { name: /Enviar/ }))

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('não pendente envia o formulário', async () => {
    const onSubmit = vi.fn((event: React.SyntheticEvent) => {
      event.preventDefault()
    })
    const user = userEvent.setup()
    render(
      <form onSubmit={onSubmit}>
        <Button type="submit">Enviar</Button>
      </form>,
    )

    await user.click(screen.getByRole('button', { name: 'Enviar' }))

    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it('respeita aria-disabled recebido de fora', async () => {
    const onClick = vi.fn()
    const user = userEvent.setup()
    render(
      <Button aria-disabled="true" onClick={onClick}>
        Remover
      </Button>,
    )

    await user.click(screen.getByRole('button', { name: 'Remover' }))

    expect(onClick).not.toHaveBeenCalled()
  })

  it.each(['primary', 'secondary', 'danger'] as const)(
    'variante %s não tem violações do axe',
    async (variant) => {
      const { container } = render(<Button variant={variant}>Ação</Button>)

      expect(await axe(container)).toHaveNoViolations()
    },
  )

  it('pendente não tem violações do axe', async () => {
    const { container } = render(<Button pending>Criar pedido</Button>)

    expect(await axe(container)).toHaveNoViolations()
  })
})
