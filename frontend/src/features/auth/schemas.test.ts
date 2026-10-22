import { describe, expect, it } from 'vitest'
import { loginSchema, registerSchema } from './schemas'

const valid = { name: 'Ana Souza', email: 'ana@exemplo.com', password: 'segredo123' }

function issueFor(result: ReturnType<typeof registerSchema.safeParse>, field: string) {
  return result.error?.issues.find((issue) => issue.path[0] === field)?.message
}

describe('registerSchema (espelha o spec user-auth)', () => {
  it('aceita um cadastro válido e apara nome e e-mail', () => {
    const parsed = registerSchema.parse({
      ...valid,
      name: '  Ana Souza ',
      email: ' ana@exemplo.com ',
    })

    expect(parsed).toEqual(valid)
  })

  it('corpo vazio: nome, e-mail e senha obrigatórios', () => {
    const result = registerSchema.safeParse({ name: '', email: '', password: '' })

    expect(issueFor(result, 'name')).toBe('é obrigatório')
    expect(issueFor(result, 'email')).toBe('é obrigatório')
    expect(issueFor(result, 'password')).toBe('é obrigatória')
  })

  it.each([
    ['   ', 'é obrigatório'],
    ['A', 'deve ter entre 2 e 100 caracteres'],
    ['n'.repeat(101), 'deve ter entre 2 e 100 caracteres'],
  ])('nome %j é recusado', (name, message) => {
    expect(issueFor(registerSchema.safeParse({ ...valid, name }), 'name')).toBe(message)
  })

  it('nome com 2 e com 100 caracteres é aceito', () => {
    expect(registerSchema.safeParse({ ...valid, name: 'Al' }).success).toBe(true)
    expect(registerSchema.safeParse({ ...valid, name: 'n'.repeat(100) }).success).toBe(true)
  })

  it.each(['abc', 'ana@', '@exemplo.com', 'ana@exemplo', 'ana exemplo@x.com', 'a@b@c.com'])(
    'e-mail %j é recusado',
    (email) => {
      expect(issueFor(registerSchema.safeParse({ ...valid, email }), 'email')).toBe(
        'deve ser um e-mail válido',
      )
    },
  )

  it('e-mail com mais de 254 caracteres é recusado', () => {
    const email = `${'a'.repeat(250)}@x.com`

    expect(issueFor(registerSchema.safeParse({ ...valid, email }), 'email')).toBe(
      'deve ter no máximo 254 caracteres',
    )
  })

  it.each(['1234567', 'abcdefgh1'.slice(0, 7), 'a1', 'x'.repeat(73)])(
    'senha %j fora do tamanho é recusada',
    (password) => {
      expect(issueFor(registerSchema.safeParse({ ...valid, password }), 'password')).toBe(
        'deve ter entre 8 e 72 caracteres',
      )
    },
  )

  it.each(['abcdefgh', '12345678', '!!!!!!!!'])(
    'senha sem letra e dígito %j é recusada',
    (password) => {
      expect(issueFor(registerSchema.safeParse({ ...valid, password }), 'password')).toBe(
        'deve conter ao menos uma letra e um dígito',
      )
    },
  )

  it('senha com 8 e com 72 caracteres válidos é aceita', () => {
    expect(registerSchema.safeParse({ ...valid, password: 'abcdefg1' }).success).toBe(true)
    expect(registerSchema.safeParse({ ...valid, password: `a1${'x'.repeat(70)}` }).success).toBe(
      true,
    )
  })

  it('senha com até 72 caracteres mas mais de 72 bytes é recusada (limite do BCrypt)', () => {
    const password = `${'é'.repeat(40)}1`

    expect(password.length).toBeLessThanOrEqual(72)
    expect(issueFor(registerSchema.safeParse({ ...valid, password }), 'password')).toBe(
      'deve ter no máximo 72 bytes (acentos contam mais de um)',
    )
  })

  it('letras acentuadas contam como letra', () => {
    expect(registerSchema.safeParse({ ...valid, password: 'ççççççç1' }).success).toBe(true)
  })
})

describe('loginSchema', () => {
  it('aceita e-mail válido e qualquer senha preenchida', () => {
    expect(loginSchema.safeParse({ email: 'ana@exemplo.com', password: 'x' }).success).toBe(true)
  })

  it('senha ausente', () => {
    const result = loginSchema.safeParse({ email: 'ana@exemplo.com', password: '' })

    expect(result.error?.issues[0]).toMatchObject({ path: ['password'], message: 'é obrigatória' })
  })

  it.each(['ana@', 'abc', ''])('e-mail %j é recusado', (email) => {
    const result = loginSchema.safeParse({ email, password: 'segredo123' })

    expect(result.error?.issues[0]?.path).toEqual(['email'])
  })

  it('não impõe regra de força na senha do login (contas antigas continuam entrando)', () => {
    expect(loginSchema.safeParse({ email: 'ana@exemplo.com', password: '123' }).success).toBe(true)
  })
})
