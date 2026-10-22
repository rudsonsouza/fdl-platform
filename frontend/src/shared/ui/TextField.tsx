import { useId, type InputHTMLAttributes, type Ref } from 'react'

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'required'> {
  label: string
  /** Mensagem de erro do campo; quando presente o campo fica `aria-invalid`. */
  error?: string | undefined
  hint?: string
  required?: boolean
  ref?: Ref<HTMLInputElement>
}

/** Campo com rótulo visível associado, dica opcional e erro ligado por `aria-describedby`. */
export function TextField({
  label,
  error,
  hint,
  required = false,
  id,
  className,
  ref,
  ...rest
}: TextFieldProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  const hintId = `${inputId}-hint`
  const errorId = `${inputId}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ')

  return (
    <div className="field">
      <label htmlFor={inputId}>
        {label}
        {required && (
          <span className="required">
            {' '}
            <span aria-hidden="true">*</span>
            <span className="sr-only">(obrigatório)</span>
          </span>
        )}
      </label>
      {hint && (
        <p id={hintId} className="field-hint">
          {hint}
        </p>
      )}
      <input
        {...rest}
        ref={ref}
        id={inputId}
        className={className}
        aria-required={required || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
      />
      {error && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
    </div>
  )
}
