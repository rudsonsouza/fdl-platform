import type { OrderStatus } from '../ui/orderStatus'

export interface OrderListFilters {
  status?: OrderStatus
  page?: number
  size?: number
}

/** Chaves do TanStack Query: um único lugar evita divergência entre leitura e invalidação. */
export const queryKeys = {
  orders: ['orders'] as const,
  orderList: (filters: OrderListFilters) => ['orders', filters] as const,
  order: (id: string) => ['orders', 'detail', id] as const,
}
