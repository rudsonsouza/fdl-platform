import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../shared/api/ApiError'

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        refetchOnWindowFocus: true,
        // Erros 4xx não melhoram ao repetir; só falhas de rede/5xx merecem nova tentativa.
        retry: (failureCount, error) =>
          failureCount < 2 &&
          !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
    },
  })
}
