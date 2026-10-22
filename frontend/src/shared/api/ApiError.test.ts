import { describe, expect, it } from 'vitest'
import { ApiError, NETWORK_ERROR, UNEXPECTED_RESPONSE } from './ApiError'

describe('ApiError', () => {
  it('lê o Problem Details do back-end, com erros por campo', () => {
    const error = ApiError.fromProblem(400, {
      title: 'Dados inválidos',
      detail: 'Um ou mais campos são inválidos.',
      code: 'VALIDATION_ERROR',
      traceId: 'abc',
      errors: [{ field: 'deliveryAddress.zipCode', message: 'deve ter 8 dígitos' }],
    })

    expect(error).toBeInstanceOf(Error)
    expect(error.status).toBe(400)
    expect(error.code).toBe('VALIDATION_ERROR')
    expect(error.message).toBe('Um ou mais campos são inválidos.')
    expect(error.traceId).toBe('abc')
    expect(error.fieldErrors).toEqual([
      { field: 'deliveryAddress.zipCode', message: 'deve ter 8 dígitos' },
    ])
  })

  it('ignora itens de errors[] malformados', () => {
    const error = ApiError.fromProblem(400, {
      errors: [{ field: 'name', message: 'ok' }, { field: 1 } as never, null as never],
    })

    expect(error.fieldErrors).toEqual([{ field: 'name', message: 'ok' }])
  })

  it('usa valores padrão quando o corpo vem incompleto', () => {
    const error = ApiError.fromProblem(500, {})

    expect(error.code).toBe(UNEXPECTED_RESPONSE)
    expect(error.fieldErrors).toEqual([])
    expect(error.detail).toBe('Erro inesperado')
  })

  it('distingue falha de rede e 401', () => {
    expect(ApiError.network().code).toBe(NETWORK_ERROR)
    expect(ApiError.network().status).toBe(0)
    expect(ApiError.fromProblem(401, { code: 'UNAUTHENTICATED' }).isUnauthorized).toBe(true)
    expect(ApiError.fromProblem(409, {}).isUnauthorized).toBe(false)
  })
})
