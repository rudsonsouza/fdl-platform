import { useAuthStore } from '../shared/session/useAuthStore'

function base64Url(value: string): string {
  const bytes = new TextEncoder().encode(value)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export interface TestTokenOptions {
  sub?: string
  name?: string
  email?: string
  /** Segundos até expirar (negativo = já expirado). */
  expiresInSeconds?: number
}

/** JWT de teste (sem assinatura válida): a aplicação só lê as claims, quem valida é o servidor. */
export function makeToken({
  sub = '11111111-1111-1111-1111-111111111111',
  name = 'Ana Souza',
  email = 'ana@exemplo.com',
  expiresInSeconds = 3600,
}: TestTokenOptions = {}): string {
  const header = base64Url(JSON.stringify({ alg: 'HS256' }))
  const exp = Math.floor(Date.now() / 1000) + expiresInSeconds
  const payload = base64Url(JSON.stringify({ iss: 'fdl-platform', sub, name, email, exp }))
  return `${header}.${payload}.assinatura`
}

/** Abre uma sessão como se o login já tivesse acontecido. */
export function signIn(options?: TestTokenOptions): string {
  const token = makeToken(options)
  useAuthStore.getState().login(token)
  return token
}
