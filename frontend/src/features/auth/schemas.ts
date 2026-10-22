import { z } from 'zod'

/** Mesmo padrão do back-end (`AuthPatterns.EMAIL`): algo@dominio.tld, sem espaços. */
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

const email = z
  .string()
  .trim()
  .min(1, 'é obrigatório')
  .max(254, 'deve ter no máximo 254 caracteres')
  .regex(EMAIL_PATTERN, 'deve ser um e-mail válido')

/** Espelha as regras de `RegisterRequest` do spec `user-auth` (mesmos limites da API). */
export const registerSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'é obrigatório')
    .min(2, 'deve ter entre 2 e 100 caracteres')
    .max(100, 'deve ter entre 2 e 100 caracteres'),
  email,
  password: z
    .string()
    .min(1, 'é obrigatória')
    .min(8, 'deve ter entre 8 e 72 caracteres')
    .max(72, 'deve ter entre 8 e 72 caracteres')
    // O BCrypt só lê 72 bytes; o back-end recusa acima disso, e o contador de caracteres não basta (acentos).
    .refine((value) => new TextEncoder().encode(value).length <= 72, {
      message: 'deve ter no máximo 72 bytes (acentos contam mais de um)',
    })
    .regex(/\p{L}/u, 'deve conter ao menos uma letra e um dígito')
    .regex(/\d/, 'deve conter ao menos uma letra e um dígito'),
})

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'é obrigatória'),
})

export type RegisterValues = z.infer<typeof registerSchema>
export type LoginValues = z.infer<typeof loginSchema>
