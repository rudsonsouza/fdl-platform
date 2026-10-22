import { useAuthStore } from '../session/useAuthStore'
import { ApiError, UNEXPECTED_RESPONSE, type ProblemBody } from './ApiError'

const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/+$/, '')

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH'
  body?: unknown
  query?: Record<string, string | number | undefined>
  signal?: AbortSignal
  /** `false` nas chamadas públicas (login/cadastro): sem cabeçalho e sem logout em 401. */
  authenticated?: boolean
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  const search = params.toString()
  return `${baseUrl}${path}${search ? `?${search}` : ''}`
}

async function toApiError(response: Response): Promise<ApiError> {
  const isJson = response.headers.get('content-type')?.includes('json') ?? false
  if (isJson) {
    try {
      return ApiError.fromProblem(response.status, (await response.json()) as ProblemBody)
    } catch {
      // corpo JSON ilegível: cai no erro genérico abaixo
    }
  }
  return new ApiError({
    status: response.status,
    code: UNEXPECTED_RESPONSE,
    title: response.statusText || 'Erro',
    detail: 'O servidor respondeu de forma inesperada. Tente novamente.',
  })
}

/**
 * Único ponto que chama `fetch`. Injeta o token, converte `application/problem+json` em
 * {@link ApiError} e, num 401 de chamada autenticada, encerra a sessão (o roteador leva ao login).
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, signal, authenticated = true } = options
  const headers = new Headers({ Accept: 'application/json, application/problem+json' })
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  const { token } = useAuthStore.getState()
  if (authenticated && token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    // Cancelamento (troca de tela, nova consulta) não é falha de rede; segue como veio.
    if ((error as { name?: unknown } | null)?.name === 'AbortError') throw error
    throw ApiError.network()
  }

  if (!response.ok) {
    const error = await toApiError(response)
    if (error.isUnauthorized && authenticated) useAuthStore.getState().logout('expired')
    throw error
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const apiClient = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiRequest<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiRequest<T>(path, { ...options, method: 'POST', body }),
  patch: <T>(path: string, body: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiRequest<T>(path, { ...options, method: 'PATCH', body }),
}
