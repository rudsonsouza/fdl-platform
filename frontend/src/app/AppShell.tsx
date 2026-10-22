import { useEffect } from 'react'
import { Link, Outlet, useNavigate } from 'react-router'
import { useAnnounce } from '../shared/a11y/announce'
import { MAIN_ID, SkipLink } from '../shared/a11y/SkipLink'
import { useRouteAnnouncer } from '../shared/a11y/useRouteAnnouncer'
import { Button } from '../shared/ui/Button'
import { hasActiveSession, useAuthStore } from '../shared/session/useAuthStore'

/** Moldura de todas as telas: skip link, marcos semânticos, navegação e a ação "Sair". */
export function AppShell() {
  const authenticated = useAuthStore(hasActiveSession)
  const user = useAuthStore((state) => state.user)
  const notice = useAuthStore((state) => state.notice)
  const logout = useAuthStore((state) => state.logout)
  const announce = useAnnounce()
  const navigate = useNavigate()
  useRouteAnnouncer()

  useEffect(() => {
    if (notice === 'expired') announce('Sua sessão expirou')
  }, [notice, announce])

  const signOut = () => {
    logout('signed-out')
    void navigate('/login')
  }

  return (
    <>
      <SkipLink />
      <header className="app-header">
        <p className="brand">Rastreador de pedidos</p>
        <nav aria-label="Principal">
          <ul className="nav-list">
            {authenticated ? (
              <>
                <li>
                  <Link to="/orders">Pedidos</Link>
                </li>
                <li>
                  <Link to="/orders/new">Novo pedido</Link>
                </li>
                <li className="nav-user">
                  <span>{user?.name}</span>
                </li>
                <li>
                  <Button variant="secondary" onClick={signOut}>
                    Sair
                  </Button>
                </li>
              </>
            ) : (
              <>
                <li>
                  <Link to="/login">Entrar</Link>
                </li>
                <li>
                  <Link to="/register">Criar conta</Link>
                </li>
              </>
            )}
          </ul>
        </nav>
      </header>
      <main id={MAIN_ID} tabIndex={-1}>
        <Outlet />
      </main>
    </>
  )
}
