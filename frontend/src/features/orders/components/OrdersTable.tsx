import { formatBRL } from '../../../shared/lib/money'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import type { OrderSummary } from '../types'

const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

export function OrdersTable({ orders, caption }: { orders: OrderSummary[]; caption: string }) {
  return (
    <div className="table-wrap">
      <table>
        <caption>{caption}</caption>
        <thead>
          <tr>
            <th scope="col">Cliente</th>
            <th scope="col" className="num">
              Itens
            </th>
            <th scope="col" className="num">
              Total
            </th>
            <th scope="col">Status</th>
            <th scope="col">Criado em</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr key={order.id}>
              <th scope="row">{order.customerName}</th>
              <td className="num">{order.totalItems}</td>
              <td className="num">{formatBRL(order.totalCents)}</td>
              <td>
                <StatusBadge status={order.status} />
              </td>
              <td>
                <time dateTime={order.createdAt}>{dateTime.format(new Date(order.createdAt))}</time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
