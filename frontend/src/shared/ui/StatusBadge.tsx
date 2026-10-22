import { STATUS_LABELS, type OrderStatus } from './orderStatus'

/** O status é sempre dito em texto; a cor só reforça (contraste AA nas classes `.badge-*`). */
export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>
}
