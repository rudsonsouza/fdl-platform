import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <>
      <h1>Página não encontrada</h1>
      <p>
        O endereço não existe. <Link to="/orders">Voltar para os pedidos</Link>.
      </p>
    </>
  )
}
