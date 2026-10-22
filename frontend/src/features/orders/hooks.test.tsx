import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { queryKeys } from '../../shared/api/queryKeys'
import { API, problem } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { signIn } from '../../test/token'
import { useCreateOrder, useOrders } from './hooks'
import type { CreateOrderPayload } from './types'

function setup() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return { queryClient, wrapper }
}

const payload: CreateOrderPayload = {
  customer: { name: 'Carlos', phone: '11987654321' },
  items: [{ name: 'Pizza', quantity: 1, unitPriceCents: 5490 }],
  deliveryAddress: {
    street: 'Rua A',
    number: '10',
    neighborhood: 'Centro',
    city: 'São Paulo',
    state: 'SP',
    zipCode: '01001000',
  },
}

describe('useOrders', () => {
  it('busca a lista com o token e traz os totais', async () => {
    signIn()
    const { wrapper } = setup()

    const { result } = renderHook(() => useOrders({}), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })
    expect(result.current.data?.totalElements).toBe(2)
    expect(result.current.data?.content[0]).toMatchObject({
      customerName: 'Maria Silva',
      totalItems: 2,
    })
  })

  it('envia status e página na consulta e guarda cada filtro numa chave própria', async () => {
    signIn()
    const seen: string[] = []
    server.use(
      http.get(`${API}/orders`, ({ request }) => {
        seen.push(new URL(request.url).search)
        return HttpResponse.json({
          content: [],
          page: 1,
          size: 100,
          totalElements: 0,
          totalPages: 0,
        })
      }),
    )
    const { wrapper, queryClient } = setup()

    const { result } = renderHook(() => useOrders({ status: 'EM_PREPARO', page: 1 }), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })
    expect(seen).toEqual(['?status=EM_PREPARO&page=1'])
    expect(
      queryClient.getQueryData(queryKeys.orderList({ status: 'EM_PREPARO', page: 1 })),
    ).toBeDefined()
  })

  it('recarrega ao voltar o foco para a janela (refetchOnWindowFocus)', async () => {
    signIn()
    let calls = 0
    server.use(
      http.get(`${API}/orders`, () => {
        calls += 1
        return HttpResponse.json({
          content: [],
          page: 0,
          size: 100,
          totalElements: 0,
          totalPages: 0,
        })
      }),
    )
    const { wrapper } = setup()
    const { result } = renderHook(() => useOrders({}), { wrapper })
    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    act(() => {
      window.dispatchEvent(new Event('visibilitychange'))
    })

    await waitFor(() => {
      expect(calls).toBe(2)
    })
  })

  it('expõe o erro tipado da API', async () => {
    signIn()
    server.use(
      http.get(`${API}/orders`, () =>
        problem({ status: 500, code: 'INTERNAL_ERROR', title: 'Erro interno', detail: 'Falhou' }),
      ),
    )
    const { wrapper } = setup()

    const { result } = renderHook(() => useOrders({}), { wrapper })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })
    expect(result.current.error).toMatchObject({ status: 500, code: 'INTERNAL_ERROR' })
  })
})

describe('useCreateOrder', () => {
  it('cria o pedido e invalida todas as listas (["orders"])', async () => {
    signIn()
    const { wrapper, queryClient } = setup()
    const list = renderHook(() => useOrders({}), { wrapper })
    await waitFor(() => {
      expect(list.result.current.data?.totalElements).toBe(2)
    })
    const create = renderHook(() => useCreateOrder(), { wrapper })

    await act(async () => {
      await create.result.current.mutateAsync(payload)
    })

    await waitFor(() => {
      expect(list.result.current.data?.totalElements).toBe(3)
    })
    expect(queryClient.getQueryState(queryKeys.orderList({}))?.isInvalidated).toBe(false)
  })

  it('não invalida a lista quando a API recusa o pedido', async () => {
    signIn()
    const { wrapper } = setup()
    const create = renderHook(() => useCreateOrder(), { wrapper })

    await act(async () => {
      await create.result.current
        .mutateAsync({
          ...payload,
          deliveryAddress: { ...payload.deliveryAddress, zipCode: '123' },
        })
        .catch(() => undefined)
    })

    await waitFor(() => {
      expect(create.result.current.error).toMatchObject({
        status: 400,
        fieldErrors: [{ field: 'deliveryAddress.zipCode' }],
      })
    })
  })
})
