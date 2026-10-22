import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { useAnnounce } from './announce'

function Announcer({ text }: { text: string }) {
  const announce = useAnnounce()
  return (
    <button
      type="button"
      onClick={() => {
        announce(text)
      }}
    >
      Avisar
    </button>
  )
}

describe('LiveRegion', () => {
  it('anuncia a mensagem numa região polite', async () => {
    const { user } = renderWithProviders(<Announcer text="Pedido criado" />)
    const region = screen.getByRole('status')
    expect(region).toHaveAttribute('aria-live', 'polite')
    expect(region).toBeEmptyDOMElement()

    await user.click(screen.getByRole('button', { name: 'Avisar' }))

    expect(await screen.findByText('Pedido criado')).toBeInTheDocument()
  })

  it('anuncia de novo a mesma frase', async () => {
    const { user } = renderWithProviders(<Announcer text="Item removido" />)
    const button = screen.getByRole('button', { name: 'Avisar' })

    await user.click(button)
    expect(await screen.findByText('Item removido')).toBeInTheDocument()
    await user.click(button)

    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(await screen.findByText('Item removido')).toBeInTheDocument()
  })

  it('useAnnounce fora do provider avisa o erro de uso', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    expect(() => render(<Announcer text="x" />)).toThrow('LiveRegionProvider')

    consoleError.mockRestore()
  })
})
