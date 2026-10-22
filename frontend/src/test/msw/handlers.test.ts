import { describe, expect, it } from 'vitest'
import { apiClient } from '../../shared/api/apiClient'
import { signIn } from '../token'

interface Page {
  content: { id: string; status: string; customerName: string }[]
  totalElements: number
}

describe('handlers MSW (contrato usado pelas telas)', () => {
  it('cadastro: 201, 400 com errors[] e 409 para e-mail repetido', async () => {
    await expect(
      apiClient.post(
        '/auth/register',
        { name: 'Ana', email: 'nova@exemplo.com', password: 'segredo123' },
        { authenticated: false },
      ),
    ).resolves.toMatchObject({ email: 'nova@exemplo.com' })

    await expect(
      apiClient.post('/auth/register', {}, { authenticated: false }),
    ).rejects.toMatchObject({
      status: 400,
      fieldErrors: [{ field: 'name' }, { field: 'email' }, { field: 'password' }],
    })

    await expect(
      apiClient.post(
        '/auth/register',
        { name: 'Ana', email: 'EXISTENTE@exemplo.com', password: 'segredo123' },
        { authenticated: false },
      ),
    ).rejects.toMatchObject({ status: 409, code: 'EMAIL_ALREADY_REGISTERED' })
  })

  it('login: token no sucesso e 401 genérico na falha', async () => {
    await expect(
      apiClient.post(
        '/auth/login',
        { email: 'ana@exemplo.com', password: 'segredo123' },
        { authenticated: false },
      ),
    ).resolves.toMatchObject({ tokenType: 'Bearer', expiresIn: 3600 })

    await expect(
      apiClient.post(
        '/auth/login',
        { email: 'ana@exemplo.com', password: 'errada' },
        { authenticated: false },
      ),
    ).rejects.toMatchObject({ status: 401, code: 'INVALID_CREDENTIALS' })
  })

  it('/auth/me e /orders exigem token (401 sem ele)', async () => {
    await expect(apiClient.get('/auth/me', { authenticated: false })).rejects.toMatchObject({
      status: 401,
    })
    await expect(apiClient.get('/orders', { authenticated: false })).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHENTICATED',
    })
    signIn()
    await expect(apiClient.get('/auth/me')).resolves.toMatchObject({ email: 'ana@exemplo.com' })
  })

  it('lista pedidos, mais recentes primeiro, com filtro por status e paginação', async () => {
    signIn()

    const all = await apiClient.get<Page>('/orders')
    expect(all.content.map((o) => o.customerName)).toEqual(['Maria Silva', 'João Pereira'])

    const inPrep = await apiClient.get<Page>('/orders', { query: { status: 'EM_PREPARO' } })
    expect(inPrep.content).toHaveLength(1)
    expect(inPrep.content[0]?.status).toBe('EM_PREPARO')

    const second = await apiClient.get<Page>('/orders', { query: { page: 1, size: 1 } })
    expect(second.content.map((o) => o.customerName)).toEqual(['João Pereira'])
    expect(second.totalElements).toBe(2)
  })

  it('cria pedido (201 com total) e devolve 400 por campo para CEP inválido', async () => {
    signIn()
    const valid = {
      customer: { name: 'Carlos', phone: '11987654321' },
      items: [
        { name: 'Pizza', quantity: 1, unitPriceCents: 5490 },
        { name: 'Refri', quantity: 2, unitPriceCents: 1200 },
      ],
      deliveryAddress: { zipCode: '01310100' },
    }

    await expect(apiClient.post('/orders', valid)).resolves.toMatchObject({
      status: 'RECEBIDO',
      totalCents: 7890,
    })
    expect((await apiClient.get<Page>('/orders')).totalElements).toBe(3)

    await expect(
      apiClient.post('/orders', { ...valid, deliveryAddress: { zipCode: '123' } }),
    ).rejects.toMatchObject({
      status: 400,
      fieldErrors: [{ field: 'deliveryAddress.zipCode' }],
    })
  })
})
