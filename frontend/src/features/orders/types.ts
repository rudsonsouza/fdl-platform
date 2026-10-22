import type { OrderStatus } from '../../shared/ui/orderStatus'

/** Linha da listagem (`GET /orders`). */
export interface OrderSummary {
  id: string
  customerName: string
  status: OrderStatus
  /** Soma das quantidades de todos os itens. */
  totalItems: number
  totalCents: number
  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Corpo de `POST /orders` (mesmos nomes da API; `id`, `status` e `totalCents` são do servidor). */
export interface CreateOrderPayload {
  customer: { name: string; phone: string }
  items: { name: string; quantity: number; unitPriceCents: number }[]
  deliveryAddress: {
    street: string
    number: string
    complement?: string
    neighborhood: string
    city: string
    state: string
    zipCode: string
  }
}

export interface CreatedOrder {
  id: string
  status: OrderStatus
  totalCents: number
}
