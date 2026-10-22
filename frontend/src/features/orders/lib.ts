import { parseReaisToCents } from '../../shared/lib/money'
import type { CreateOrderValues } from './schemas'
import type { CreateOrderPayload } from './types'

/** Total em centavos dos itens que já têm quantidade e preço válidos (ignora linhas incompletas). */
export function totalCents(items: CreateOrderValues['items']): number {
  return items.reduce((sum, item) => {
    const cents = parseReaisToCents(item.unitPrice)
    const quantity = /^\d+$/.test(item.quantity.trim()) ? Number(item.quantity) : 0
    return sum + (cents === null ? 0 : cents * quantity)
  }, 0)
}

/** Valores do formulário (texto) → corpo da API (números, complemento vazio omitido). */
export function toPayload(values: CreateOrderValues): CreateOrderPayload {
  const { complement, ...address } = values.deliveryAddress
  return {
    customer: { name: values.customer.name, phone: values.customer.phone },
    items: values.items.map((item) => ({
      name: item.name,
      quantity: Number(item.quantity),
      unitPriceCents: parseReaisToCents(item.unitPrice) ?? 0,
    })),
    deliveryAddress: { ...address, ...(complement ? { complement } : {}) },
  }
}
