import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import { decodeJwt } from '../lib/jwt'

export interface SessionUser {
  id: string
  name: string
  email: string
}

/** Por que não há mais sessão; a tela de login usa isso para explicar ao usuário. */
export type SessionNotice = 'expired' | 'signed-out'

interface AuthState {
  token: string | null
  user: SessionUser | null
  /** Expiração do token em ms desde a época. */
  expiresAt: number | null
  notice: SessionNotice | null
  login: (token: string) => void
  logout: (notice?: SessionNotice) => void
  clearNotice: () => void
}

let expiryTimer: ReturnType<typeof setTimeout> | undefined
// Limite do setTimeout (2^31-1 ms); acima disso o navegador dispara na hora.
const MAX_TIMEOUT = 2_147_483_647

function cancelExpiry() {
  if (expiryTimer !== undefined) clearTimeout(expiryTimer)
  expiryTimer = undefined
}

function scheduleExpiry(expiresAt: number) {
  cancelExpiry()
  const delay = Math.min(Math.max(expiresAt - Date.now(), 0), MAX_TIMEOUT)
  expiryTimer = setTimeout(() => {
    // Se ainda falta tempo (delay truncado), agenda de novo em vez de encerrar a sessão.
    if (Date.now() < expiresAt) scheduleExpiry(expiresAt)
    else useAuthStore.getState().logout('expired')
  }, delay)
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      expiresAt: null,
      notice: null,
      login: (token) => {
        const claims = decodeJwt(token)
        scheduleExpiry(claims.expiresAt)
        set({
          token,
          user: { id: claims.sub, name: claims.name, email: claims.email },
          expiresAt: claims.expiresAt,
          notice: null,
        })
      },
      logout: (notice = 'signed-out') => {
        cancelExpiry()
        set({ token: null, user: null, expiresAt: null, notice })
      },
      clearNotice: () => {
        set({ notice: null })
      },
    }),
    {
      name: 'fdl.session',
      // sessionStorage: a sessão some ao fechar a aba (requisito do spec).
      storage: createJSONStorage(() => sessionStorage),
      partialize: ({ token, user, expiresAt }) => ({ token, user, expiresAt }),
      onRehydrateStorage: () => (state) => {
        if (!state?.token || state.expiresAt === null) return
        if (state.expiresAt <= Date.now()) state.logout('expired')
        else scheduleExpiry(state.expiresAt)
      },
    },
  ),
)

/** Sessão válida agora (token presente e não expirado). */
export function hasActiveSession(state: Pick<AuthState, 'token' | 'expiresAt'>): boolean {
  return state.token !== null && state.expiresAt !== null && state.expiresAt > Date.now()
}
