interface SpinnerProps {
  /** Texto lido por leitores de tela; o desenho em si é decorativo. */
  label?: string
}

export function Spinner({ label = 'Carregando' }: SpinnerProps) {
  return (
    <span className="spinner-wrap">
      <span className="spinner" aria-hidden="true" />
      <span className="sr-only">{label}</span>
    </span>
  )
}
