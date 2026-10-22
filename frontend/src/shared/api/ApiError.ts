/** Um item de `errors[]` do Problem Details de validação (campo no formato `items[0].quantity`). */
export interface FieldError {
  field: string
  message: string
}

export interface ProblemBody {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  traceId?: string
  errors?: FieldError[]
  [extra: string]: unknown
}

/** Código sintético para falhas sem resposta HTTP (rede fora do ar, CORS, etc.). */
export const NETWORK_ERROR = 'NETWORK_ERROR'
/** Código sintético para respostas de erro que não vieram em Problem Details. */
export const UNEXPECTED_RESPONSE = 'UNEXPECTED_RESPONSE'

/** Erro de uma chamada à API, já com o Problem Details (RFC 9457) da resposta tipado. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly title: string
  readonly detail: string
  readonly traceId: string | undefined
  readonly fieldErrors: FieldError[]
  readonly problem: ProblemBody | undefined

  constructor(init: {
    status: number
    code: string
    title: string
    detail: string
    traceId?: string
    fieldErrors?: FieldError[]
    problem?: ProblemBody
  }) {
    super(init.detail || init.title)
    this.name = 'ApiError'
    this.status = init.status
    this.code = init.code
    this.title = init.title
    this.detail = init.detail
    this.traceId = init.traceId
    this.fieldErrors = init.fieldErrors ?? []
    this.problem = init.problem
  }

  static fromProblem(status: number, problem: ProblemBody): ApiError {
    const fieldErrors = Array.isArray(problem.errors)
      ? problem.errors.filter(
          (e): e is FieldError =>
            typeof e === 'object' &&
            typeof (e as Partial<FieldError> | null)?.field === 'string' &&
            typeof (e as Partial<FieldError>).message === 'string',
        )
      : []
    return new ApiError({
      status,
      code: problem.code ?? UNEXPECTED_RESPONSE,
      title: problem.title ?? 'Erro',
      detail: problem.detail ?? problem.title ?? 'Erro inesperado',
      traceId: problem.traceId,
      fieldErrors,
      problem,
    })
  }

  static network(): ApiError {
    return new ApiError({
      status: 0,
      code: NETWORK_ERROR,
      title: 'Sem conexão',
      detail: 'Não foi possível falar com o servidor. Verifique sua conexão e tente novamente.',
    })
  }

  get isUnauthorized(): boolean {
    return this.status === 401
  }
}
