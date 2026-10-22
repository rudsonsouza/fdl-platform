import { Navigate, type RouteObject } from 'react-router'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { RegisterPage } from '../features/auth/pages/RegisterPage'
import { NewOrderPage } from '../features/orders/pages/NewOrderPage'
import { OrderListPage } from '../features/orders/pages/OrderListPage'
import { AppShell } from './AppShell'
import { NotFoundPage } from './NotFoundPage'
import { ProtectedRoute } from './ProtectedRoute'

export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/orders" replace /> },
      { path: 'login', element: <LoginPage />, handle: { title: 'Entrar' } },
      { path: 'register', element: <RegisterPage />, handle: { title: 'Criar conta' } },
      {
        element: <ProtectedRoute />,
        children: [
          { path: 'orders', element: <OrderListPage />, handle: { title: 'Pedidos' } },
          { path: 'orders/new', element: <NewOrderPage />, handle: { title: 'Novo pedido' } },
        ],
      },
      { path: '*', element: <NotFoundPage />, handle: { title: 'Página não encontrada' } },
    ],
  },
]
