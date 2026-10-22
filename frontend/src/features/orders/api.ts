import { apiClient } from '../../shared/api/apiClient'
import type { OrderListFilters } from '../../shared/api/queryKeys'
import type { CreatedOrder, CreateOrderPayload, OrderSummary, Page } from './types'

export const listOrders = (filters: OrderListFilters, signal?: AbortSignal) =>
  apiClient.get<Page<OrderSummary>>('/orders', {
    query: { status: filters.status, page: filters.page, size: filters.size },
    ...(signal ? { signal } : {}),
  })

export const createOrder = (payload: CreateOrderPayload) =>
  apiClient.post<CreatedOrder>('/orders', payload)
