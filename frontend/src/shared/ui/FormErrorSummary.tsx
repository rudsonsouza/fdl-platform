import { useEffect, useRef, type MouseEvent } from 'react'

export interface SummaryError {
  /** Id do elemento do campo, para o link e o foco. */
  fieldId: string
  message: string
}

interface FormErrorSummaryProps {
  errors: SummaryError[]
  /** Muda a cada envio com erro; garante que o foco volte ao resumo mesmo com os mesmos erros. */
  submitCount: number
  title?: string
}

/**
 * Resumo de erros que recebe o foco quando o envio falha e leva a cada campo inválido.
 * Os links usam o foco direto no elemento, sem mexer na URL (o roteador não é afetado).
 */
export function FormErrorSummary({
  errors,
  submitCount,
  title = 'Corrija os campos abaixo para continuar',
}: FormErrorSummaryProps) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (errors.length > 0) ref.current?.focus()
    // Só reage a um novo envio; mudanças de texto dos erros não devem roubar o foco.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitCount])

  if (errors.length === 0) return null

  const focusField = (event: MouseEvent<HTMLAnchorElement>, fieldId: string) => {
    event.preventDefault()
    document.getElementById(fieldId)?.focus()
  }

  return (
    <div ref={ref} role="alert" tabIndex={-1} className="error-summary">
      <h2 className="error-summary-title">{title}</h2>
      <ul>
        {errors.map((error) => (
          <li key={`${error.fieldId}-${error.message}`}>
            <a
              href={`#${error.fieldId}`}
              onClick={(event) => {
                focusField(event, error.fieldId)
              }}
            >
              {error.message}
            </a>
          </li>
        ))}
      </ul>
    </div>
  )
}
