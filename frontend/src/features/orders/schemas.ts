import { z } from 'zod'
import { parseReaisToCents } from '../../shared/lib/money'

const required = 'é obrigatório'

const text = (max: number) =>
  z.string().trim().min(1, required).max(max, `deve ter no máximo ${max} caracteres`)

const MAX_PRICE_CENTS = 10_000_000

const itemSchema = z.object({
  name: text(120),
  // Campos numéricos ficam como texto no formulário (o usuário digita); a API recebe número.
  quantity: z
    .string()
    .trim()
    .min(1, 'é obrigatória')
    .regex(/^\d+$/, 'deve ser um número inteiro')
    .refine((v) => Number(v) >= 1 && Number(v) <= 99, 'deve ser de 1 a 99'),
  unitPrice: z
    .string()
    .trim()
    .min(1, required)
    .refine((v) => parseReaisToCents(v) !== null, 'informe um valor em reais, como 12,50')
    .refine(
      (v) => (parseReaisToCents(v) ?? 0) <= MAX_PRICE_CENTS,
      'deve ser no máximo R$ 100.000,00',
    ),
})

/** Espelha as regras de `CreateOrderRequest` do spec `order-management`. */
export const createOrderSchema = z.object({
  customer: z.object({
    name: z
      .string()
      .trim()
      .min(1, required)
      .min(2, 'deve ter entre 2 e 100 caracteres')
      .max(100, 'deve ter entre 2 e 100 caracteres'),
    phone: z
      .string()
      .min(1, required)
      .regex(/^\d{10,11}$/, 'deve ter 10 ou 11 dígitos, somente números, com DDD'),
  }),
  items: z
    .array(itemSchema)
    .min(1, 'deve ter ao menos 1 item')
    .max(50, 'deve ter no máximo 50 itens'),
  deliveryAddress: z.object({
    street: text(120),
    number: text(120),
    complement: z.string().trim().max(120, 'deve ter no máximo 120 caracteres'),
    neighborhood: text(120),
    city: text(120),
    state: z
      .string()
      .min(1, required)
      .regex(/^[A-Z]{2}$/, 'deve ter exatamente 2 letras maiúsculas, como SP'),
    zipCode: z
      .string()
      .min(1, required)
      .regex(/^\d{8}$/, 'deve ter exatamente 8 dígitos, sem hífen'),
  }),
})

export type CreateOrderValues = z.infer<typeof createOrderSchema>

export const EMPTY_ITEM: CreateOrderValues['items'][number] = {
  name: '',
  quantity: '1',
  unitPrice: '',
}
