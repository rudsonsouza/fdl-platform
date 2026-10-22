import type { FieldErrors, UseFormSetError } from 'react-hook-form'
import type { ApiError } from '../../shared/api/ApiError'
import type { SummaryError } from '../../shared/ui/FormErrorSummary'
import type { CreateOrderValues } from './schemas'

type FieldPath = string

const LABELS: Record<string, string> = {
  'customer.name': 'Cliente: nome',
  'customer.phone': 'Cliente: telefone',
  'deliveryAddress.street': 'Endereço: rua',
  'deliveryAddress.number': 'Endereço: número',
  'deliveryAddress.complement': 'Endereço: complemento',
  'deliveryAddress.neighborhood': 'Endereço: bairro',
  'deliveryAddress.city': 'Endereço: cidade',
  'deliveryAddress.state': 'Endereço: UF',
  'deliveryAddress.zipCode': 'Endereço: CEP',
}

const ITEM_FIELDS: Record<string, string> = {
  name: 'nome',
  quantity: 'quantidade',
  unitPrice: 'preço unitário',
}

/** Id do elemento de cada campo, usado pelos links do resumo e pelo foco. */
export function fieldId(path: FieldPath): string {
  return `f-${path.replace(/\./g, '-')}`
}

function labelFor(path: FieldPath): string {
  const item = /^items\.(\d+)\.(\w+)$/.exec(path)
  if (item) return `Item ${Number(item[1]) + 1}: ${ITEM_FIELDS[item[2] ?? ''] ?? item[2] ?? ''}`
  if (path === 'items') return 'Itens'
  return LABELS[path] ?? path
}

function collect(node: unknown, path: FieldPath, out: { path: FieldPath; message: string }[]) {
  if (node === null || typeof node !== 'object') return
  const { message, type } = node as { message?: unknown; type?: unknown }
  if (typeof message === 'string' && typeof type === 'string') {
    out.push({ path, message })
  }
  for (const [key, child] of Object.entries(node)) {
    if (key === 'ref' || key === 'message' || key === 'type' || key === 'types') continue
    collect(child, path ? `${path}.${key}` : key, out)
  }
}

/** Todos os erros do formulário, na ordem em que o RHF os guarda, prontos para o resumo. */
export function toSummary(errors: FieldErrors<CreateOrderValues>): SummaryError[] {
  const found: { path: FieldPath; message: string }[] = []
  collect(errors, '', found)
  return found.map(({ path, message }) => {
    // O erro da lista de itens fica em `items.root` (ou direto em `items`); o campo é o mesmo.
    const clean = path.replace(/\.root$/, '')
    return { fieldId: fieldId(clean), message: `${labelFor(clean)}: ${message}` }
  })
}

const KNOWN_PATH =
  /^(customer\.(name|phone)|items(\.\d+\.(name|quantity|unitPrice))?|deliveryAddress\.(street|number|complement|neighborhood|city|state|zipCode))$/

/** `items[0].unitPriceCents` (API) → `items.0.unitPrice` (formulário). */
export function toFormPath(apiField: string): string {
  return apiField.replace(/\[(\d+)\]/g, '.$1').replace(/\.unitPriceCents$/, '.unitPrice')
}

/**
 * Liga os erros `400` do servidor aos campos. Devolve as mensagens que não correspondem a nenhum
 * campo do formulário, para o chamador mostrá-las como erro geral.
 */
export function applyServerErrors(
  error: ApiError,
  setError: UseFormSetError<CreateOrderValues>,
): string[] {
  const unmatched: string[] = []
  for (const { field, message } of error.fieldErrors) {
    const path = toFormPath(field)
    if (KNOWN_PATH.test(path)) {
      setError(path as Parameters<typeof setError>[0], { type: 'server', message })
    } else {
      unmatched.push(`${field}: ${message}`)
    }
  }
  return unmatched
}
