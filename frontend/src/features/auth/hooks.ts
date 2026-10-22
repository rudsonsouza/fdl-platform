import { useMutation } from '@tanstack/react-query'
import { useAuthStore } from '../../shared/session/useAuthStore'
import { loginUser, registerUser } from './api'

export function useRegister() {
  return useMutation({ mutationFn: registerUser })
}

/** Faz o login e abre a sessão com o token recebido. */
export function useLogin() {
  const openSession = useAuthStore((state) => state.login)
  return useMutation({
    mutationFn: loginUser,
    onSuccess: (token) => {
      openSession(token.accessToken)
    },
  })
}
