const DEFAULT_REDIRECT = '/orders'

/** Caminho de login que lembra para onde voltar (`/login?redirect=/orders`). */
export function loginPath(redirect?: string): string {
  if (!redirect) return '/login'
  return `/login?redirect=${encodeURIComponent(redirect).replace(/%2F/gi, '/')}`
}

/**
 * Só aceita destinos dentro da própria aplicação ("/algo"). Valores como "//evil.com",
 * "https://evil.com" ou "/\\evil.com" voltam para a lista, evitando redirecionamento aberto.
 */
export function safeRedirect(value: string | null | undefined): string {
  if (!value?.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return DEFAULT_REDIRECT
  }
  return value
}
