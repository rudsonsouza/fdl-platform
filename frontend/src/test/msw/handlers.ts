import { http, HttpResponse } from 'msw'
import type { OrderStatus } from '../../shared/ui/orderStatus'
import { makeToken } from '../token'

export const API = 'http://localhost:8080/api/v1'

export interface ProblemOptions {
  status: number
  code: string
  title: string
  detail: string
  errors?: { field: string; message: string }[]
}

/** Resposta de erro no formato Problem Details que o back-end devolve. */
export function problem({ status, code, title, detail, errors }: ProblemOptions) {
  return HttpResponse.json(
    {
      type: `urn:fdl:problem:${code.toLowerCase().replace(/_/g, '-')}`,
      title,
      status,
      detail,
      instance: '/api/v1/test',
      code,
      traceId: 'trace-test-0001',
      ...(errors ? { errors } : {}),
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

export const unauthenticated = () =>
  problem({
    status: 401,
    code: 'UNAUTHENTICATED',
    title: 'Autenticação necessária',
    detail: 'Autenticação necessária ou token inválido.',
  })

export interface OrderFixture {
  id: string
  customerName: string
  status: OrderStatus
  totalCents: number
  totalItems: number
  createdAt: string
  updatedAt: string
}

const seed: OrderFixture[] = [
  {
    id: '22222222-2222-2222-2222-222222222222',
    customerName: 'Maria Silva',
    status: 'RECEBIDO',
    totalCents: 7890,
    totalItems: 2,
    createdAt: '2026-01-02T10:00:00Z',
    updatedAt: '2026-01-02T10:00:00Z',
  },
  {
    id: '33333333-3333-3333-3333-333333333333',
    customerName: 'João Pereira',
    status: 'EM_PREPARO',
    totalCents: 4500,
    totalItems: 1,
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T11:00:00Z',
  },
]

/** Pedidos em memória; `resetFixtures` volta ao estado inicial entre os testes. */
export let orders: OrderFixture[] = structuredClone(seed)
export const registeredEmails = new Set<string>(['existente@exemplo.com'])

export function resetFixtures() {
  orders = structuredClone(seed)
  registeredEmails.clear()
  registeredEmails.add('existente@exemplo.com')
}

function hasBearer(request: Request): boolean {
  return request.headers.get('Authorization')?.startsWith('Bearer ') ?? false
}

interface RegisterBody {
  name?: string
  email?: string
  password?: string
}

interface CreateOrderBody {
  customer?: { name?: string; phone?: string }
  items?: { name: string; quantity: number; unitPriceCents: number }[]
  deliveryAddress?: Record<string, string | undefined>
}

export const handlers = [
  http.post(`${API}/auth/register`, async ({ request }) => {
    const body = (await request.json()) as RegisterBody
    const errors: { field: string; message: string }[] = []
    if (!body.name?.trim()) errors.push({ field: 'name', message: 'é obrigatório' })
    if (!body.email?.includes('@'))
      errors.push({ field: 'email', message: 'deve ser um e-mail válido' })
    if (!body.password || body.password.length < 8) {
      errors.push({ field: 'password', message: 'deve ter entre 8 e 72 caracteres' })
    }
    if (errors.length > 0) {
      return problem({
        status: 400,
        code: 'VALIDATION_ERROR',
        title: 'Dados inválidos',
        detail: 'Um ou mais campos são inválidos.',
        errors,
      })
    }
    const email = (body.email ?? '').toLowerCase()
    if (registeredEmails.has(email)) {
      return problem({
        status: 409,
        code: 'EMAIL_ALREADY_REGISTERED',
        title: 'E-mail já cadastrado',
        detail: 'Já existe uma conta com este e-mail.',
      })
    }
    registeredEmails.add(email)
    return HttpResponse.json(
      { id: '11111111-1111-1111-1111-111111111111', name: body.name, email },
      { status: 201 },
    )
  }),

  http.post(`${API}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string }
    if (body.email?.toLowerCase() === 'ana@exemplo.com' && body.password === 'segredo123') {
      return HttpResponse.json({
        accessToken: makeToken(),
        tokenType: 'Bearer',
        expiresIn: 3600,
      })
    }
    return problem({
      status: 401,
      code: 'INVALID_CREDENTIALS',
      title: 'Credenciais inválidas',
      detail: 'E-mail ou senha inválidos.',
    })
  }),

  http.get(`${API}/auth/me`, ({ request }) => {
    if (!hasBearer(request)) return unauthenticated()
    return HttpResponse.json({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Ana Souza',
      email: 'ana@exemplo.com',
    })
  }),

  http.get(`${API}/orders`, ({ request }) => {
    if (!hasBearer(request)) return unauthenticated()
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 100)
    const filtered = orders
      .filter((o) => !status || o.status === status)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    return HttpResponse.json({
      content: filtered.slice(page * size, page * size + size),
      page,
      size,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
    })
  }),

  http.post(`${API}/orders`, async ({ request }) => {
    if (!hasBearer(request)) return unauthenticated()
    const body = (await request.json()) as CreateOrderBody
    const zip = body.deliveryAddress?.zipCode ?? ''
    if (!/^\d{8}$/.test(zip)) {
      return problem({
        status: 400,
        code: 'VALIDATION_ERROR',
        title: 'Dados inválidos',
        detail: 'Um ou mais campos são inválidos.',
        errors: [
          { field: 'deliveryAddress.zipCode', message: 'deve ter exatamente 8 dígitos, sem hífen' },
        ],
      })
    }
    const items = body.items ?? []
    const created: OrderFixture = {
      id: crypto.randomUUID(),
      customerName: body.customer?.name ?? '',
      status: 'RECEBIDO',
      totalCents: items.reduce((sum, i) => sum + i.quantity * i.unitPriceCents, 0),
      totalItems: items.length,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    orders = [created, ...orders]
    return HttpResponse.json(created, {
      status: 201,
      headers: { Location: `/api/v1/orders/${created.id}` },
    })
  }),
]
