import type { QueryClient } from '@tanstack/react-query'
import { QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { LiveRegionProvider } from '../shared/a11y/LiveRegion'
import { createQueryClient } from './queryClient'

export function AppProviders({
  children,
  queryClient,
}: {
  children: ReactNode
  queryClient?: QueryClient
}) {
  const [client] = useState(() => queryClient ?? createQueryClient())
  return (
    <QueryClientProvider client={client}>
      <LiveRegionProvider>{children}</LiveRegionProvider>
    </QueryClientProvider>
  )
}
