export interface JwtClaims {
  sub: string
  email: string
  name: string
  /** Expiração em milissegundos desde a época. */
  expiresAt: number
}

function base64UrlToString(value: string): string {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/**
 * Lê as claims do payload do JWT para montar a sessão. Não valida a assinatura: quem confia no
 * token é o servidor, que o verifica a cada requisição.
 */
export function decodeJwt(token: string): JwtClaims {
  const payload = token.split('.')[1]
  if (!payload) throw new Error('Token inválido')
  try {
    const claims = JSON.parse(base64UrlToString(payload)) as Record<string, unknown>
    const { sub, email, name, exp } = claims
    if (
      typeof sub !== 'string' ||
      typeof email !== 'string' ||
      typeof name !== 'string' ||
      typeof exp !== 'number'
    ) {
      throw new Error('claims ausentes')
    }
    return { sub, email, name, expiresAt: exp * 1000 }
  } catch {
    throw new Error('Token inválido')
  }
}
