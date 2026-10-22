import { describe, expect, it } from 'vitest'
import { totalCents, toPayload } from './lib'
import { createOrderSchema, EMPTY_ITEM, type CreateOrderValues } from './schemas'

const valid: CreateOrderValues = {
  customer: { name: 'Maria Silva', phone: '11987654321' },
  items: [
    { name: 'Pizza', quantity: '1', unitPrice: '54,90' },
    { name: 'Refrigerante', quantity: '2', unitPrice: '12' },
  ],
  deliveryAddress: {
    street: 'Avenida Paulista',
    number: '1000',
    complement: '',
    neighborhood: 'Bela Vista',
    city: 'São Paulo',
    state: 'SP',
    zipCode: '01310100',
  },
}

function messages(values: unknown) {
  const result = createOrderSchema.safeParse(values)
  // Como o React Hook Form, fica com a primeira mensagem de cada campo.
  const first: Record<string, string> = {}
  for (const issue of result.error?.issues ?? []) first[issue.path.join('.')] ??= issue.message
  return first
}

function withCustomer(patch: Partial<CreateOrderValues['customer']>) {
  return { ...valid, customer: { ...valid.customer, ...patch } }
}

function withAddress(patch: Partial<CreateOrderValues['deliveryAddress']>) {
  return { ...valid, deliveryAddress: { ...valid.deliveryAddress, ...patch } }
}

function withItem(patch: Partial<CreateOrderValues['items'][number]>) {
  return { ...valid, items: [{ ...EMPTY_ITEM, name: 'Pizza', unitPrice: '10', ...patch }] }
}

describe('createOrderSchema (espelha o spec order-management)', () => {
  it('aceita um pedido válido, com complemento opcional', () => {
    expect(createOrderSchema.safeParse(valid).success).toBe(true)
    expect(createOrderSchema.safeParse(withAddress({ complement: 'Apto 12' })).success).toBe(true)
  })

  it.each([
    ['', 'customer.name', 'é obrigatório'],
    ['   ', 'customer.name', 'é obrigatório'],
    ['A', 'customer.name', 'deve ter entre 2 e 100 caracteres'],
    ['n'.repeat(101), 'customer.name', 'deve ter entre 2 e 100 caracteres'],
  ])('nome do cliente %j', (name, path, message) => {
    expect(messages(withCustomer({ name }))[path]).toBe(message)
  })

  it.each(['11abc', '119876543', '119876543210', '(11)98765-4321', ' 11987654321'])(
    'telefone %j é recusado',
    (phone) => {
      expect(messages(withCustomer({ phone }))['customer.phone']).toBe(
        'deve ter 10 ou 11 dígitos, somente números, com DDD',
      )
    },
  )

  it('telefone com 10 e com 11 dígitos é aceito', () => {
    expect(createOrderSchema.safeParse(withCustomer({ phone: '1198765432' })).success).toBe(true)
    expect(createOrderSchema.safeParse(withCustomer({ phone: '11987654321' })).success).toBe(true)
  })

  it('lista de itens vazia ou com mais de 50 itens', () => {
    expect(messages({ ...valid, items: [] }).items).toBe('deve ter ao menos 1 item')
    const many = Array.from({ length: 51 }, () => ({ ...EMPTY_ITEM, name: 'x', unitPrice: '1' }))
    expect(messages({ ...valid, items: many }).items).toBe('deve ter no máximo 50 itens')
    expect(createOrderSchema.safeParse({ ...valid, items: many.slice(0, 50) }).success).toBe(true)
  })

  it('item sem nome', () => {
    expect(messages(withItem({ name: '' }))['items.0.name']).toBe('é obrigatório')
    expect(messages(withItem({ name: 'p'.repeat(121) }))['items.0.name']).toBe(
      'deve ter no máximo 120 caracteres',
    )
  })

  it.each([
    ['0', 'deve ser de 1 a 99'],
    ['-1', 'deve ser um número inteiro'],
    ['100', 'deve ser de 1 a 99'],
    ['1.5', 'deve ser um número inteiro'],
    ['dois', 'deve ser um número inteiro'],
    ['', 'é obrigatória'],
  ])('quantidade %j', (quantity, message) => {
    expect(messages(withItem({ quantity }))['items.0.quantity']).toBe(message)
  })

  it('quantidade 1 e 99 são aceitas', () => {
    expect(createOrderSchema.safeParse(withItem({ quantity: '1' })).success).toBe(true)
    expect(createOrderSchema.safeParse(withItem({ quantity: '99' })).success).toBe(true)
  })

  it.each(['abc', '12,345', '-1', '1.234,56', 'R$ 5'])('preço %j é recusado', (unitPrice) => {
    expect(messages(withItem({ unitPrice }))['items.0.unitPrice']).toBe(
      'informe um valor em reais, como 12,50',
    )
  })

  it('preço vazio, zero e o máximo de R$ 100.000,00', () => {
    expect(messages(withItem({ unitPrice: '' }))['items.0.unitPrice']).toBe('é obrigatório')
    expect(createOrderSchema.safeParse(withItem({ unitPrice: '0' })).success).toBe(true)
    expect(createOrderSchema.safeParse(withItem({ unitPrice: '100000' })).success).toBe(true)
    expect(messages(withItem({ unitPrice: '100000,01' }))['items.0.unitPrice']).toBe(
      'deve ser no máximo R$ 100.000,00',
    )
  })

  it.each(['street', 'number', 'neighborhood', 'city'] as const)(
    'endereço: %s vazio, em branco ou acima de 120 caracteres',
    (field) => {
      const path = `deliveryAddress.${field}`
      expect(messages(withAddress({ [field]: '' }))[path]).toBe('é obrigatório')
      expect(messages(withAddress({ [field]: '   ' }))[path]).toBe('é obrigatório')
      expect(messages(withAddress({ [field]: 'x'.repeat(121) }))[path]).toBe(
        'deve ter no máximo 120 caracteres',
      )
    },
  )

  it('complemento acima de 120 caracteres', () => {
    expect(
      messages(withAddress({ complement: 'x'.repeat(121) }))['deliveryAddress.complement'],
    ).toBe('deve ter no máximo 120 caracteres')
  })

  it.each(['sp', 'SPA', 'S', 'S1', 'São'])('UF %j é recusada', (state) => {
    expect(messages(withAddress({ state }))['deliveryAddress.state']).toBe(
      'deve ter exatamente 2 letras maiúsculas, como SP',
    )
  })

  it.each(['01310-100', '123', '0131010a', '013101000'])('CEP %j é recusado', (zipCode) => {
    expect(messages(withAddress({ zipCode }))['deliveryAddress.zipCode']).toBe(
      'deve ter exatamente 8 dígitos, sem hífen',
    )
  })

  it('CEP vazio é obrigatório', () => {
    expect(messages(withAddress({ zipCode: '' }))['deliveryAddress.zipCode']).toBe('é obrigatório')
  })

  it('corpo vazio lista cliente, itens e endereço', () => {
    const result = createOrderSchema.safeParse({})

    expect(new Set(result.error?.issues.map((i) => i.path[0]))).toEqual(
      new Set(['customer', 'items', 'deliveryAddress']),
    )
  })
})

describe('totalCents', () => {
  it('2 × R$ 12,00 + 1 × R$ 54,90 = R$ 78,90', () => {
    expect(totalCents(valid.items)).toBe(7890)
  })

  it('ignora linhas incompletas ou inválidas', () => {
    expect(
      totalCents([
        { name: '', quantity: '', unitPrice: '10' },
        { name: '', quantity: '2', unitPrice: 'abc' },
        { name: '', quantity: '1.5', unitPrice: '10' },
        { name: '', quantity: '3', unitPrice: '1,50' },
      ]),
    ).toBe(450)
  })

  it('lista vazia soma zero', () => {
    expect(totalCents([])).toBe(0)
  })
})

describe('toPayload', () => {
  it('converte texto em números/centavos e usa os nomes da API', () => {
    const payload = toPayload(valid)

    expect(payload.items).toEqual([
      { name: 'Pizza', quantity: 1, unitPriceCents: 5490 },
      { name: 'Refrigerante', quantity: 2, unitPriceCents: 1200 },
    ])
    expect(payload.customer).toEqual({ name: 'Maria Silva', phone: '11987654321' })
  })

  it('omite o complemento vazio e mantém o preenchido', () => {
    expect(toPayload(valid).deliveryAddress).not.toHaveProperty('complement')
    expect(toPayload(withAddress({ complement: 'Apto 12' })).deliveryAddress.complement).toBe(
      'Apto 12',
    )
  })
})
