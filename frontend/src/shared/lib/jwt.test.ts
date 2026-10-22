import { describe, expect, it } from 'vitest'
import { makeToken } from '../../test/token'
import { decodeJwt } from './jwt'

describe('decodeJwt', () => {
  it('lê sub, e-mail, nome (com acentos) e expiração', () => {
    const before = Date.now()
    const claims = decodeJwt(
      makeToken({ name: 'José d’Ávila', email: 'jose@exemplo.com', sub: 'abc' }),
    )

    expect(claims).toMatchObject({ sub: 'abc', email: 'jose@exemplo.com', name: 'José d’Ávila' })
    expect(claims.expiresAt).toBeGreaterThan(before)
  })

  it.each(['', 'a', 'a.b.c', 'a.@@@.c'])('rejeita token inválido %j', (token) => {
    expect(() => decodeJwt(token)).toThrow('Token inválido')
  })

  it('rejeita payload sem as claims esperadas', () => {
    const payload = btoa(JSON.stringify({ sub: 'x' })).replace(/=+$/, '')

    expect(() => decodeJwt(`h.${payload}.s`)).toThrow('Token inválido')
  })
})
