import type { ButtonHTMLAttributes, MouseEvent } from 'react'
import { Spinner } from './Spinner'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger'
  /** Operação em andamento: o botão segue focável, mas não dispara de novo (`aria-disabled`). */
  pending?: boolean
  pendingLabel?: string
}

/**
 * Botão com estado pendente. Em vez de `disabled` (que tira o foco do botão e some para leitores de
 * tela), usa `aria-disabled` e ignora o clique, o que também bloqueia o envio duplicado do formulário.
 */
export function Button({
  variant = 'primary',
  pending = false,
  pendingLabel,
  type = 'button',
  onClick,
  children,
  className,
  ...rest
}: ButtonProps) {
  const handleClick = (event: MouseEvent<HTMLButtonElement>) => {
    if (pending || rest['aria-disabled'] === true || rest['aria-disabled'] === 'true') {
      event.preventDefault()
      return
    }
    onClick?.(event)
  }

  return (
    <button
      {...rest}
      type={type}
      className={['btn', `btn-${variant}`, className].filter(Boolean).join(' ')}
      aria-disabled={pending ? true : rest['aria-disabled']}
      onClick={handleClick}
    >
      {pending && <Spinner label={pendingLabel ?? 'Aguarde'} />}
      {children}
    </button>
  )
}
