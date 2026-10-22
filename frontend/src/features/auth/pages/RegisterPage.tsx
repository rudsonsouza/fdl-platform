import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../../../shared/api/ApiError'
import { loginPath, safeRedirect } from '../../../shared/lib/redirect'
import { Button } from '../../../shared/ui/Button'
import { FormErrorSummary } from '../../../shared/ui/FormErrorSummary'
import { TextField } from '../../../shared/ui/TextField'
import { applyServerFieldErrors, toSummary } from '../formErrors'
import { useRegister } from '../hooks'
import { registerSchema, type RegisterValues } from '../schemas'

const FIELDS = [
  { name: 'name', label: 'Nome', id: 'register-name' },
  { name: 'email', label: 'E-mail', id: 'register-email' },
  { name: 'password', label: 'Senha', id: 'register-password' },
] as const

export function RegisterPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const redirect = params.get('redirect')
  const register = useRegister()
  const [serverMessage, setServerMessage] = useState<string | null>(null)
  const [failedSubmits, setFailedSubmits] = useState(0)

  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { name: '', email: '', password: '' },
  })
  const { errors } = form.formState

  const onSubmit = (values: RegisterValues) => {
    setServerMessage(null)
    register.mutate(values, {
      onSuccess: (user) => {
        // O login abre com o e-mail preenchido e anuncia a criação da conta (ver LoginPage).
        const destination = redirect ? loginPath(safeRedirect(redirect)) : '/login'
        void navigate(destination, { state: { registeredEmail: user.email } })
      },
      onError: (error) => {
        if (!(error instanceof ApiError)) throw error
        const mapped =
          error.code === 'EMAIL_ALREADY_REGISTERED'
            ? (form.setError('email', { type: 'server', message: 'já está cadastrado' }), true)
            : applyServerFieldErrors(error, form.setError, ['name', 'email', 'password'])
        if (mapped) setFailedSubmits((n) => n + 1)
        else setServerMessage(error.detail)
      },
    })
  }

  const summary = toSummary(errors, FIELDS)

  return (
    <>
      <h1>Criar conta</h1>
      <form
        noValidate
        onSubmit={(event) => {
          void form.handleSubmit(onSubmit, () => {
            setFailedSubmits((n) => n + 1)
          })(event)
        }}
        aria-describedby={serverMessage ? 'register-server-error' : undefined}
      >
        <FormErrorSummary errors={summary} submitCount={failedSubmits} />
        {serverMessage && (
          <p id="register-server-error" role="alert" className="form-alert">
            {serverMessage}
          </p>
        )}
        <TextField
          id="register-name"
          label="Nome"
          autoComplete="name"
          required
          error={errors.name?.message}
          {...form.register('name')}
        />
        <TextField
          id="register-email"
          label="E-mail"
          type="email"
          autoComplete="email"
          required
          error={errors.email?.message}
          {...form.register('email')}
        />
        <TextField
          id="register-password"
          label="Senha"
          type="password"
          autoComplete="new-password"
          required
          hint="De 8 a 72 caracteres, com ao menos uma letra e um dígito."
          error={errors.password?.message}
          {...form.register('password')}
        />
        <Button type="submit" pending={register.isPending} pendingLabel="Criando conta">
          Criar conta
        </Button>
      </form>
      <p>
        Já tem conta?{' '}
        <Link to={redirect ? loginPath(safeRedirect(redirect)) : '/login'}>Entrar</Link>
      </p>
    </>
  )
}
