import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { AnnounceContext, type Announce } from './announce'

/** Região `aria-live="polite"` global: qualquer parte da tela anuncia mensagens por `useAnnounce`. */
export function LiveRegionProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState('')
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const announce = useCallback<Announce>((text) => {
    clearTimeout(timer.current)
    // Limpa antes de escrever: repetir a mesma frase precisa ser anunciado de novo.
    setMessage('')
    timer.current = setTimeout(() => {
      setMessage(text)
    }, 50)
  }, [])

  const value = useMemo(() => announce, [announce])

  return (
    <AnnounceContext value={value}>
      {children}
      <div className="sr-only" role="status" aria-live="polite" aria-atomic="true">
        {message}
      </div>
    </AnnounceContext>
  )
}
