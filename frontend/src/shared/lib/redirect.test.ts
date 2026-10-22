import { describe, expect, it } from 'vitest'
import { loginPath, safeRedirect } from './redirect'

describe('loginPath', () => {
  it('sem destino vai ao login simples', () => {
    expect(loginPath()).toBe('/login')
  })

  it('guarda o destino legível, com a query codificada', () => {
    expect(loginPath('/orders')).toBe('/login?redirect=/orders')
    expect(loginPath('/orders?status=EM_PREPARO&page=1')).toBe(
      '/login?redirect=/orders%3Fstatus%3DEM_PREPARO%26page%3D1',
    )
  })
})

describe('safeRedirect', () => {
  it('aceita caminhos internos', () => {
    expect(safeRedirect('/orders/new')).toBe('/orders/new')
    expect(safeRedirect('/orders?status=RECEBIDO')).toBe('/orders?status=RECEBIDO')
  })

  it.each([
    null,
    undefined,
    '',
    'orders',
    'https://evil.com',
    '//evil.com',
    '/\\evil.com',
    'javascript:alert(1)',
  ])('volta para a lista com %j', (value) => {
    expect(safeRedirect(value)).toBe('/orders')
  })
})
