import { Link } from 'react-router'

interface PaginationProps {
  /** Página atual, a partir de 0. */
  page: number
  totalPages: number
  /** Endereço (caminho + query) de cada página, a partir de 0. */
  hrefFor: (page: number) => string
}

/** Páginas visíveis: a primeira, a última e duas de cada lado da atual. */
function visiblePages(page: number, totalPages: number): (number | 'gap')[] {
  const wanted = new Set([0, totalPages - 1, page - 2, page - 1, page, page + 1, page + 2])
  const pages = [...wanted].filter((p) => p >= 0 && p < totalPages).sort((a, b) => a - b)
  return pages.flatMap((p, i) => (i > 0 && p - (pages[i - 1] ?? p) > 1 ? ['gap' as const, p] : [p]))
}

export function Pagination({ page, totalPages, hrefFor }: PaginationProps) {
  if (totalPages <= 1) return null

  return (
    <nav aria-label="Paginação dos pedidos" className="pagination">
      <ul>
        {page > 0 && (
          <li>
            <Link to={hrefFor(page - 1)} rel="prev">
              Anterior
            </Link>
          </li>
        )}
        {visiblePages(page, totalPages).map((p, i) =>
          p === 'gap' ? (
            <li key={`gap-${i}`} aria-hidden="true">
              …
            </li>
          ) : (
            <li key={p}>
              <Link
                to={hrefFor(p)}
                aria-current={p === page ? 'page' : undefined}
                aria-label={`Página ${p + 1}`}
              >
                {p + 1}
              </Link>
            </li>
          ),
        )}
        {page < totalPages - 1 && (
          <li>
            <Link to={hrefFor(page + 1)} rel="next">
              Próxima
            </Link>
          </li>
        )}
      </ul>
    </nav>
  )
}
