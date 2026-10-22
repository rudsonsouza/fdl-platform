import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys, type OrderListFilters } from '../../shared/api/queryKeys'
import { createOrder, listOrders } from './api'

/** Lista de pedidos. Recarrega ao voltar para a aba, para refletir status alterados por outros meios. */
export function useOrders(filters: OrderListFilters) {
  return useQuery({
    queryKey: queryKeys.orderList(filters),
    queryFn: ({ signal }) => listOrders(filters, signal),
    refetchOnWindowFocus: true,
    placeholderData: keepPreviousData,
  })
}

/** Cria o pedido e invalida toda a lista (`['orders']`, qualquer filtro/página). */
export function useCreateOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createOrder,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.orders }),
  })
}
