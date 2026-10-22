import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, problem } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { makeToken, signIn } from '../../test/token'
import { useAuthStore } from '../session/useAuthStore'
import { ApiError } from './ApiError'
import { apiClient } from './apiClient'

describe('apiClient', () => {
  it('envia o token no cabeçalho Authorization', async () => {
    const token = signIn()
    let received: string | null = null
    server.use(
      http.get(`${API}/ping`, ({ request }) => {
        received = request.headers.get('Authorization')
        return HttpResponse.json({ ok: true })
      }),
    )

    await expect(apiClient.get('/ping')).resolves.toEqual({ ok: true })
    expect(received).toBe(`Bearer ${token}`)
  })

  it('não envia o token em chamadas públicas', async () => {
    signIn()
    let received: string | null = 'não chamado'
    server.use(
      http.post(`${API}/auth/login`, ({ request }) => {
        received = request.headers.get('Authorization')
        return HttpResponse.json({})
      }),
    )

    await apiClient.post('/auth/login', {}, { authenticated: false })

    expect(received).toBeNull()
  })

  it('serializa o corpo como JSON e monta a query ignorando valores vazios', async () => {
    let seen: { body: unknown; search: string; type: string | null } | undefined
    server.use(
      http.post(`${API}/echo`, async ({ request }) => {
        seen = {
          body: await request.json(),
          search: new URL(request.url).search,
          type: request.headers.get('Content-Type'),
        }
        return HttpResponse.json({}, { status: 201 })
      }),
    )

    await apiClient.post(
      '/echo',
      { a: 1 },
      { query: { status: 'RECEBIDO', page: 0, vazio: '', nada: undefined } },
    )

    expect(seen).toEqual({
      body: { a: 1 },
      search: '?status=RECEBIDO&page=0',
      type: 'application/json',
    })
  })

  it('converte o Problem Details em ApiError tipado', async () => {
    signIn()
    server.use(
      http.get(`${API}/falha`, () =>
        problem({
          status: 400,
          code: 'VALIDATION_ERROR',
          title: 'Dados inválidos',
          detail: 'Um ou mais campos são inválidos.',
          errors: [{ field: 'name', message: 'é obrigatório' }],
        }),
      ),
    )

    const error = await apiClient.get('/falha').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
      traceId: 'trace-test-0001',
      fieldErrors: [{ field: 'name', message: 'é obrigatório' }],
    })
    expect(useAuthStore.getState().token).not.toBeNull()
  })

  it('encerra a sessão com aviso de expiração quando uma chamada autenticada recebe 401', async () => {
    signIn()
    server.use(
      http.get(`${API}/privado`, () =>
        problem({
          status: 401,
          code: 'UNAUTHENTICATED',
          title: 'Autenticação necessária',
          detail: 'x',
        }),
      ),
    )

    await expect(apiClient.get('/privado')).rejects.toMatchObject({ status: 401 })

    const state = useAuthStore.getState()
    expect(state.token).toBeNull()
    expect(state.notice).toBe('expired')
  })

  it('não derruba a sessão quando o 401 vem de uma chamada pública (login errado)', async () => {
    signIn()

    await expect(
      apiClient.post('/auth/login', { email: 'a@a.com', password: 'x' }, { authenticated: false }),
    ).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' })

    expect(useAuthStore.getState().token).not.toBeNull()
  })

  it('trata resposta de erro que não é Problem Details', async () => {
    server.use(
      http.get(`${API}/html`, () => new HttpResponse('<h1>Bad gateway</h1>', { status: 502 })),
    )

    await expect(apiClient.get('/html')).rejects.toMatchObject({
      status: 502,
      code: 'UNEXPECTED_RESPONSE',
    })
  })

  it('trata JSON de erro ilegível', async () => {
    server.use(
      http.get(
        `${API}/quebrado`,
        () =>
          new HttpResponse('{oops', {
            status: 500,
            headers: { 'Content-Type': 'application/json' },
          }),
      ),
    )

    await expect(apiClient.get('/quebrado')).rejects.toMatchObject({
      status: 500,
      code: 'UNEXPECTED_RESPONSE',
    })
  })

  it('converte falha de rede em ApiError sem status HTTP', async () => {
    server.use(http.get(`${API}/offline`, () => HttpResponse.error()))

    await expect(apiClient.get('/offline')).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    })
  })

  it('devolve undefined em 204', async () => {
    server.use(http.patch(`${API}/vazio`, () => new HttpResponse(null, { status: 204 })))

    await expect(apiClient.patch('/vazio', {})).resolves.toBeUndefined()
  })

  it('propaga o cancelamento sem virar erro de rede', async () => {
    const controller = new AbortController()
    server.use(http.get(`${API}/lento`, () => HttpResponse.json({})))
    controller.abort()

    await expect(apiClient.get('/lento', { signal: controller.signal })).rejects.toMatchObject({
      name: 'AbortError',
    })
  })

  it('usa a sessão atual: token expirado não é um bom token para testes', () => {
    expect(makeToken({ expiresInSeconds: -10 }).split('.')).toHaveLength(3)
  })
})
