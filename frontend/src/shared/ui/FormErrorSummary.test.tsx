import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { FormErrorSummary } from './FormErrorSummary'
import { TextField } from './TextField'

const errors = [
  { fieldId: 'zip', message: 'CEP deve ter 8 dígitos' },
  { fieldId: 'name', message: 'Nome é obrigatório' },
]

describe('FormErrorSummary', () => {
  it('não renderiza nada sem erros', () => {
    const { container } = render(<FormErrorSummary errors={[]} submitCount={1} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('é um alerta focado com um link por erro', () => {
    render(<FormErrorSummary errors={errors} submitCount={1} />)

    const alert = screen.getByRole('alert')
    expect(alert).toHaveFocus()
    expect(
      screen.getByRole('heading', { name: 'Corrija os campos abaixo para continuar' }),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('link')).toHaveLength(2)
  })

  it('o link leva o foco ao campo correspondente', async () => {
    const user = userEvent.setup()
    render(
      <>
        <FormErrorSummary errors={errors} submitCount={1} />
        <TextField label="CEP" id="zip" error="CEP deve ter 8 dígitos" />
        <TextField label="Nome" id="name" />
      </>,
    )

    await user.click(screen.getByRole('link', { name: 'CEP deve ter 8 dígitos' }))

    expect(screen.getByLabelText('CEP')).toHaveFocus()
  })

  it('volta o foco ao resumo a cada novo envio com erro, mesmo com os mesmos erros', async () => {
    const user = userEvent.setup()
    const { rerender } = render(
      <>
        <FormErrorSummary errors={errors} submitCount={1} />
        <TextField label="CEP" id="zip" />
      </>,
    )
    await user.click(screen.getByLabelText('CEP'))
    expect(screen.getByLabelText('CEP')).toHaveFocus()

    rerender(
      <>
        <FormErrorSummary errors={errors} submitCount={2} />
        <TextField label="CEP" id="zip" />
      </>,
    )

    expect(screen.getByRole('alert')).toHaveFocus()
  })

  it('mudar só o texto dos erros não rouba o foco', async () => {
    const user = userEvent.setup()
    const { rerender } = render(
      <>
        <FormErrorSummary errors={errors} submitCount={1} />
        <TextField label="CEP" id="zip" />
      </>,
    )
    await user.click(screen.getByLabelText('CEP'))

    rerender(
      <>
        <FormErrorSummary errors={[{ fieldId: 'zip', message: 'Outro texto' }]} submitCount={1} />
        <TextField label="CEP" id="zip" />
      </>,
    )

    expect(screen.getByLabelText('CEP')).toHaveFocus()
  })

  it('não tem violações do axe', async () => {
    const { container } = render(
      <>
        <FormErrorSummary errors={errors} submitCount={1} />
        <TextField label="CEP" id="zip" error="CEP deve ter 8 dígitos" />
        <TextField label="Nome" id="name" error="Nome é obrigatório" />
      </>,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
