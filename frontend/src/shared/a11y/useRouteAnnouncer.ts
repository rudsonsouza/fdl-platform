import { useEffect, useRef } from 'react'
import { useLocation, useMatches } from 'react-router'

export interface RouteHandle {
  title?: string
}

/**
 * A cada troca de rota atualiza o título do documento e leva o foco ao `h1` da nova tela, que o
 * leitor de tela lê. Na primeira renderização só ajusta o título (não rouba o foco do carregamento).
 */
export function useRouteAnnouncer() {
  const { pathname } = useLocation()
  const matches = useMatches()
  const first = useRef(true)

  const title = [...matches]
    .reverse()
    .map((match) => (match.handle as RouteHandle | undefined)?.title)
    .find((value) => value !== undefined)

  useEffect(() => {
    document.title = title ?? 'Rastreador de pedidos'
    if (first.current) {
      first.current = false
      return
    }
    const heading = document.querySelector<HTMLElement>('main h1')
    if (heading) {
      heading.tabIndex = -1
      heading.focus()
    }
  }, [pathname, title])
}
