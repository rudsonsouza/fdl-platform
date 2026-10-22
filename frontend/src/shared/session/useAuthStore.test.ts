import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { makeToken } from '../../test/token'
import { hasActiveSession, useAuthStore } from './useAuthStore'

describe('useAuthStore', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('login monta a sessão a partir das claims e guarda no sessionStorage', () => {
    useAuthStore.getState().login(makeToken({ name: 'Ana Souza', email: 'ana@exemplo.com' }))

    const state = useAuthStore.getState()
    expect(state.user).toEqual({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Ana Souza',
      email: 'ana@exemplo.com',
    })
    expect(hasActiveSession(state)).toBe(true)
    const stored = sessionStorage.getItem('fdl.session') ?? ''
    expect(stored).toContain('ana@exemplo.com')
    expect(localStorage.getItem('fdl.session')).toBeNull()
  })

  it('login com token inválido falha sem abrir sessão', () => {
    expect(() => {
      useAuthStore.getState().login('lixo')
    }).toThrow('Token inválido')
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('logout limpa a sessão e registra o motivo', () => {
    useAuthStore.getState().login(makeToken())

    useAuthStore.getState().logout('signed-out')

    const state = useAuthStore.getState()
    expect(state.token).toBeNull()
    expect(state.user).toBeNull()
    expect(state.notice).toBe('signed-out')
    expect(hasActiveSession(state)).toBe(false)
    expect(sessionStorage.getItem('fdl.session') ?? '').not.toContain('ana@exemplo.com')
  })

  it('encerra a sessão sozinho quando o token expira', () => {
    useAuthStore.getState().login(makeToken({ expiresInSeconds: 60 }))

    vi.advanceTimersByTime(59_000)
    expect(useAuthStore.getState().token).not.toBeNull()

    vi.advanceTimersByTime(2_000)
    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().notice).toBe('expired')
  })

  it('um novo login cancela o encerramento agendado do anterior', () => {
    useAuthStore.getState().login(makeToken({ expiresInSeconds: 30 }))
    useAuthStore.getState().login(makeToken({ expiresInSeconds: 3600 }))

    vi.advanceTimersByTime(60_000)

    expect(useAuthStore.getState().token).not.toBeNull()
  })

  it('token já expirado não conta como sessão ativa', () => {
    expect(hasActiveSession({ token: 'x', expiresAt: Date.now() - 1 })).toBe(false)
    expect(hasActiveSession({ token: null, expiresAt: Date.now() + 1000 })).toBe(false)
  })

  it('clearNotice remove o aviso', () => {
    useAuthStore.getState().logout('expired')

    useAuthStore.getState().clearNotice()

    expect(useAuthStore.getState().notice).toBeNull()
  })

  it('restaura a sessão do sessionStorage e descarta a expirada', async () => {
    const fresh = makeToken({ expiresInSeconds: 600 })
    sessionStorage.setItem(
      'fdl.session',
      JSON.stringify({
        state: {
          token: fresh,
          user: { id: 'u', name: 'Ana', email: 'a@a.com' },
          expiresAt: Date.now() + 600_000,
        },
        version: 0,
      }),
    )
    await useAuthStore.persist.rehydrate()
    expect(useAuthStore.getState().token).toBe(fresh)

    sessionStorage.setItem(
      'fdl.session',
      JSON.stringify({
        state: {
          token: 'velho',
          user: { id: 'u', name: 'Ana', email: 'a@a.com' },
          expiresAt: Date.now() - 1000,
        },
        version: 0,
      }),
    )
    await useAuthStore.persist.rehydrate()
    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().notice).toBe('expired')
  })
})
