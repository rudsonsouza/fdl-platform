import { Navigate, Outlet, useLocation } from 'react-router'
import { loginPath } from '../shared/lib/redirect'
import { hasActiveSession, useAuthStore } from '../shared/session/useAuthStore'

/** Barra quem não tem sessão e guarda o destino para voltar depois do login. */
export function ProtectedRoute() {
  const authenticated = useAuthStore(hasActiveSession)
  const { pathname, search } = useLocation()

  if (!authenticated) return <Navigate to={loginPath(pathname + search)} replace />
  return <Outlet />
}
