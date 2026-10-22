import type { MouseEvent } from 'react'

export const MAIN_ID = 'conteudo'

/** Primeiro elemento focável da página: leva direto ao conteúdo principal. */
export function SkipLink() {
  const skip = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault()
    document.getElementById(MAIN_ID)?.focus()
  }

  return (
    <a className="skip-link" href={`#${MAIN_ID}`} onClick={skip}>
      Pular para o conteúdo
    </a>
  )
}
