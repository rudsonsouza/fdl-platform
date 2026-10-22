import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createRef } from 'react'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { TextField } from './TextField'

describe('TextField', () => {
  it('associa o rótulo visível ao campo', async () => {
    const user = userEvent.setup()
    render(<TextField label="E-mail" type="email" />)

    await user.type(screen.getByLabelText('E-mail'), 'ana@exemplo.com')

    expect(screen.getByRole('textbox', { name: 'E-mail' })).toHaveValue('ana@exemplo.com')
  })

  it('sem erro não marca o campo como inválido nem aponta para mensagem', () => {
    render(<TextField label="Nome" />)
    const input = screen.getByLabelText('Nome')

    expect(input).not.toHaveAttribute('aria-invalid')
    expect(input).not.toHaveAttribute('aria-describedby')
  })

  it('com erro marca aria-invalid e liga a mensagem por aria-describedby', () => {
    render(<TextField label="CEP" error="deve ter 8 dígitos" hint="Somente números" />)
    const input = screen.getByLabelText('CEP')

    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('Somente números deve ter 8 dígitos')
    expect(screen.getByText('deve ter 8 dígitos')).toBeInTheDocument()
  })

  it('obrigatório: indica no rótulo e em aria-required, sem usar o atributo nativo', () => {
    render(<TextField label="Nome" required />)
    const input = screen.getByLabelText(/Nome/)

    expect(input).toHaveAttribute('aria-required', 'true')
    expect(input).not.toHaveAttribute('required') // validação é nossa; o navegador não deve interferir
    expect(screen.getByText('(obrigatório)')).toBeInTheDocument()
  })

  it('aceita id próprio e repassa a ref', () => {
    const ref = createRef<HTMLInputElement>()
    render(<TextField label="Telefone" id="telefone" ref={ref} />)

    expect(screen.getByLabelText('Telefone')).toHaveAttribute('id', 'telefone')
    expect(ref.current).toBe(screen.getByLabelText('Telefone'))
  })

  it('não tem violações do axe, com e sem erro', async () => {
    const { container } = render(
      <>
        <TextField label="Nome" required />
        <TextField label="CEP" error="inválido" hint="8 dígitos" />
      </>,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
