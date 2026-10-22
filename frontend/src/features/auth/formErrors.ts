import type { FieldErrors, FieldValues, Path, UseFormSetError } from 'react-hook-form'
import type { ApiError } from '../../shared/api/ApiError'
import type { SummaryError } from '../../shared/ui/FormErrorSummary'

/** Resumo de erros na ordem dos campos, com o nome do campo na frente de cada mensagem. */
export function toSummary<T extends FieldValues>(
  errors: FieldErrors<T>,
  fields: readonly { name: Path<T>; label: string; id: string }[],
): SummaryError[] {
  return fields.flatMap(({ name, label, id }) => {
    const message = errors[name]?.message
    return typeof message === 'string' ? [{ fieldId: id, message: `${label}: ${message}` }] : []
  })
}

/**
 * Coloca nos campos os erros `400` do servidor (`errors[].field`). Devolve `true` se algum erro pôde
 * ser ligado a um campo do formulário; o que sobra deve ser mostrado como erro geral.
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: ApiError,
  setError: UseFormSetError<T>,
  knownFields: readonly Path<T>[],
): boolean {
  let applied = false
  for (const { field, message } of error.fieldErrors) {
    const name = knownFields.find((known) => known === field)
    if (name) {
      setError(name, { type: 'server', message })
      applied = true
    }
  }
  return applied
}
