import { createContext, useContext } from 'react'

export type Announce = (message: string) => void

export const AnnounceContext = createContext<Announce | null>(null)

export function useAnnounce(): Announce {
  const announce = useContext(AnnounceContext)
  if (!announce) throw new Error('useAnnounce precisa estar dentro de LiveRegionProvider')
  return announce
}
