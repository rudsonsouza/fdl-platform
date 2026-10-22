import { useSearchParams, Link } from 'react-router'
import { ApiError } from '../../../shared/api/ApiError'
import { Button } from '../../../shared/ui/Button'
import { ORDER_STATUSES, STATUS_LABELS, type OrderStatus } from '../../../shared/ui/orderStatus'
import { Spinner } from '../../../shared/ui/Spinner'
import { OrdersTable } from '../components/OrdersTable'
import { Pagination } from '../components/Pagination'
import { useOrders } from '../hooks'

function parseStatus(value: string | null): OrderStatus | undefined {
  return ORDER_STATUSES.find((status) => status === value)
}

function parsePage(value: string | null): number {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : 0
}

export function OrderListPage() {
  const [params, setParams] = useSearchParams()
  const status = parseStatus(params.get('status'))
  const page = parsePage(params.get('page'))
  const orders = useOrders({ ...(status ? { status } : {}), page })

  const hrefFor = (target: number) => {
    const next = new URLSearchParams()
    if (status) next.set('status', status)
    if (target > 0) next.set('page', String(target))
    const query = next.toString()
    return query ? `/orders?${query}` : '/orders'
  }

  const changeStatus = (value: string) => {
    const next = new URLSearchParams()
    if (value) next.set('status', value)
    setParams(next)
  }

  const { data, error, isPending, isFetching, isError, refetch } = orders
  const filterLabel = status ? STATUS_LABELS[status] : null

  return (
    <>
      <div className="page-header">
        <h1>Pedidos</h1>
        <Link className="btn btn-primary" to="/orders/new">
          Novo pedido
        </Link>
      </div>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="status-filter">Filtrar por status</label>
          <select
            id="status-filter"
            value={status ?? ''}
            onChange={(event) => {
              changeStatus(event.target.value)
            }}
          >
            <option value="">Todos</option>
            {ORDER_STATUSES.map((value) => (
              <option key={value} value={value}>
                {STATUS_LABELS[value]}
              </option>
            ))}
          </select>
        </div>
        <Button
          variant="secondary"
          pending={isFetching && !isPending}
          pendingLabel="Atualizando"
          onClick={() => {
            void refetch()
          }}
        >
          Atualizar
        </Button>
      </div>

      <section aria-label="Resultado da lista" aria-busy={isFetching}>
        {isPending && <Spinner label="Carregando pedidos" />}

        {isError && (
          <div role="alert" className="form-alert">
            <p>
              {error instanceof ApiError ? error.detail : 'Não foi possível carregar os pedidos.'}
            </p>
            <Button
              variant="secondary"
              onClick={() => {
                void refetch()
              }}
            >
              Tentar novamente
            </Button>
          </div>
        )}

        {data?.content.length === 0 && (
          <div className="empty-state">
            <p>
              {filterLabel
                ? `Nenhum pedido com o status "${filterLabel}".`
                : 'Nenhum pedido registrado ainda.'}
            </p>
            <Link to="/orders/new">Criar um pedido</Link>
          </div>
        )}

        {data && data.content.length > 0 && (
          <>
            <OrdersTable
              orders={data.content}
              caption={
                filterLabel
                  ? `Pedidos com status ${filterLabel}, do mais recente ao mais antigo`
                  : 'Pedidos, do mais recente ao mais antigo'
              }
            />
            <p className="result-count">
              {data.totalElements} {data.totalElements === 1 ? 'pedido' : 'pedidos'}
              {data.totalPages > 1 && ` · página ${data.page + 1} de ${data.totalPages}`}
            </p>
            <Pagination page={data.page} totalPages={data.totalPages} hrefFor={hrefFor} />
          </>
        )}
      </section>
    </>
  )
}
