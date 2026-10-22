import { apiClient } from '../../shared/api/apiClient'
import type { LoginValues, RegisterValues } from './schemas'

export interface UserResponse {
  id: string
  name: string
  email: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  /** Validade do token em segundos. */
  expiresIn: number
}

// Cadastro e login são públicos: sem Authorization e sem derrubar a sessão num 401.
export const registerUser = (values: RegisterValues) =>
  apiClient.post<UserResponse>('/auth/register', values, { authenticated: false })

export const loginUser = (values: LoginValues) =>
  apiClient.post<TokenResponse>('/auth/login', values, { authenticated: false })
