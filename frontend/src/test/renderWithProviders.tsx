import { QueryClient } from '@tanstack/react-query'
import { render, type RenderResult } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { createMemoryRouter, MemoryRouter, RouterProvider } from 'react-router'
import { AppProviders } from '../app/providers'
import { routes } from '../app/routes'

function testQueryClient(): QueryClient {
  // Sem repetições nem cache entre testes: falhas aparecem na hora e cada teste começa limpo.
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
}

interface RenderOptions {
  route?: string
  queryClient?: QueryClient
}

interface RenderedUi extends RenderResult {
  user: UserEvent
  queryClient: QueryClient
}

/** Renderiza um componente com os providers da aplicação (Query, região viva) e um roteador em memória. */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', queryClient = testQueryClient() }: RenderOptions = {},
): RenderedUi {
  const user = userEvent.setup()
  const result = render(
    <AppProviders queryClient={queryClient}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </AppProviders>,
  )
  return { ...result, user, queryClient }
}

/** Renderiza a aplicação inteira (moldura, rotas e proteção) começando em `route`. */
export function renderApp({ route = '/', queryClient = testQueryClient() }: RenderOptions = {}) {
  const user = userEvent.setup()
  const router = createMemoryRouter(routes, { initialEntries: [route] })
  const result = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  )
  return { ...result, user, router, queryClient }
}
