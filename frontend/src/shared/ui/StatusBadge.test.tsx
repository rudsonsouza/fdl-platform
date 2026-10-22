import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { describe, expect, it } from 'vitest'
import { ORDER_STATUSES, STATUS_LABELS, type OrderStatus } from './orderStatus'
import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  it.each<[OrderStatus, string]>([
    ['RECEBIDO', 'Recebido'],
    ['EM_PREPARO', 'Em preparo'],
    ['SAIU_PARA_ENTREGA', 'Saiu para entrega'],
    ['ENTREGUE', 'Entregue'],
    ['CANCELADO', 'Cancelado'],
  ])('%s é dito em texto: %s', (status, label) => {
    render(<StatusBadge status={status} />)

    expect(screen.getByText(label)).toBeVisible()
  })

  it('cobre todos os status da API, cada um com texto próprio', () => {
    expect(ORDER_STATUSES).toHaveLength(5)
    expect(new Set(ORDER_STATUSES.map((s) => STATUS_LABELS[s])).size).toBe(5)
  })

  it('não tem violações do axe em nenhum status', async () => {
    const { container } = render(
      <ul>
        {ORDER_STATUSES.map((status) => (
          <li key={status}>
            <StatusBadge status={status} />
          </li>
        ))}
      </ul>,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
