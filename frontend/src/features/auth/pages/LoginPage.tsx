import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useAnnounce } from '../../../shared/a11y/announce'
import { ApiError } from '../../../shared/api/ApiError'
import { safeRedirect } from '../../../shared/lib/redirect'
import { useAuthStore } from '../../../shared/session/useAuthStore'
import { Button } from '../../../shared/ui/Button'
import { FormErrorSummary } from '../../../shared/ui/FormErrorSummary'
import { TextField } from '../../../shared/ui/TextField'
import { applyServerFieldErrors, toSummary } from '../formErrors'
import { useLogin } from '../hooks'
import { loginSchema, type LoginValues } from '../schemas'

const FIELDS = [
  { name: 'email', label: 'E-mail', id: 'login-email' },
  { name: 'password', label: 'Senha', id: 'login-password' },
] as const

interface LoginLocationState {
  registeredEmail?: string
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const announce = useAnnounce()
  const login = useLogin()
  const notice = useAuthStore((state) => state.notice)
  const registeredEmail = (location.state as LoginLocationState | null)?.registeredEmail
  const [serverMessage, setServerMessage] = useState<string | null>(null)
  const [failedSubmits, setFailedSubmits] = useState(0)

  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: registeredEmail ?? '', password: '' },
  })
  const { errors } = form.formState

  useEffect(() => {
    if (registeredEmail) announce('Conta criada. Entre com seu e-mail e senha.')
  }, [registeredEmail, announce])

  const onSubmit = (values: LoginValues) => {
    setServerMessage(null)
    login.mutate(values, {
      onSuccess: () => {
        void navigate(safeRedirect(params.get('redirect')), { replace: true })
      },
      onError: (error) => {
        if (!(error instanceof ApiError)) throw error
        if (
          error.status === 400 &&
          applyServerFieldErrors(error, form.setError, ['email', 'password'])
        ) {
          setFailedSubmits((n) => n + 1)
        } else if (error.code === 'INVALID_CREDENTIALS') {
          // Mensagem única para e-mail inexistente e senha errada, como no back-end.
          setServerMessage('E-mail ou senha inválidos.')
          form.resetField('password')
        } else {
          setServerMessage(error.detail)
        }
      },
    })
  }

  const summary = toSummary(errors, FIELDS)

  return (
    <>
      <h1>Entrar</h1>
      {registeredEmail && (
        <p className="form-notice">Conta criada. Entre com seu e-mail e senha.</p>
      )}
      {notice === 'expired' && (
        <p className="form-notice">Sua sessão expirou. Entre novamente para continuar.</p>
      )}
      <form
        noValidate
        onSubmit={(event) => {
          void form.handleSubmit(onSubmit, () => {
            setFailedSubmits((n) => n + 1)
          })(event)
        }}
      >
        <FormErrorSummary errors={summary} submitCount={failedSubmits} />
        {serverMessage && (
          <p role="alert" className="form-alert">
            {serverMessage}
          </p>
        )}
        <TextField
          id="login-email"
          label="E-mail"
          type="email"
          autoComplete="email"
          required
          error={errors.email?.message}
          {...form.register('email')}
        />
        <TextField
          id="login-password"
          label="Senha"
          type="password"
          autoComplete="current-password"
          required
          error={errors.password?.message}
          {...form.register('password')}
        />
        <Button type="submit" pending={login.isPending} pendingLabel="Entrando">
          Entrar
        </Button>
      </form>
      <p>
        Ainda não tem conta?{' '}
        <Link to={params.get('redirect') ? `/register?${params.toString()}` : '/register'}>
          Criar conta
        </Link>
      </p>
    </>
  )
}
