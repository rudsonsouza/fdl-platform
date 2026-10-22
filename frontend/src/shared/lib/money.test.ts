import { describe, expect, it } from 'vitest'
import { formatBRL, parseReaisToCents } from './money'

describe('formatBRL', () => {
  it.each([
    [0, 'R$ 0,00'],
    [7890, 'R$ 78,90'],
    [1200, 'R$ 12,00'],
    [5, 'R$ 0,05'],
    [123456789, 'R$ 1.234.567,89'],
  ])('%i centavos → %s', (cents, text) => {
    expect(formatBRL(cents).normalize('NFKC')).toBe(text)
  })
})

describe('parseReaisToCents', () => {
  it.each([
    ['12', 1200],
    ['12,5', 1250],
    ['12,50', 1250],
    ['12.50', 1250],
    ['0,05', 5],
    ['0', 0],
    [' 54,90 ', 5490],
    ['100000', 10_000_000],
  ])('%j → %i', (input, cents) => {
    expect(parseReaisToCents(input)).toBe(cents)
  })

  it.each(['', ' ', 'abc', '12,345', '1.234,56', '-5', '12,', ',50', '1e3', 'R$ 12', '12 reais'])(
    'recusa %j',
    (input) => {
      expect(parseReaisToCents(input)).toBeNull()
    },
  )

  it('recusa números grandes demais para serem exatos', () => {
    expect(parseReaisToCents('9'.repeat(30))).toBeNull()
  })
})
