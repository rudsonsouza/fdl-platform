const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

/** 7890 → "R$ 78,90" (o espaço entre o símbolo e o valor é um espaço sem quebra, como no navegador). */
export function formatBRL(cents: number): string {
  return brl.format(cents / 100)
}

/**
 * Converte o que o usuário digitou em reais ("12", "12,5", "12,50", "12.50") para centavos.
 * Retorna `null` se não for um valor monetário simples (sem separador de milhar, até 2 decimais).
 */
export function parseReaisToCents(input: string): number | null {
  const match = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(input.trim())
  if (!match) return null
  const reais = Number(match[1])
  const cents = Number((match[2] ?? '').padEnd(2, '0'))
  const total = reais * 100 + cents
  return Number.isSafeInteger(total) ? total : null
}
