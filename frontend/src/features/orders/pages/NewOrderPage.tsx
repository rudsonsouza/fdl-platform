import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState } from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { useAnnounce } from '../../../shared/a11y/announce'
import { ApiError } from '../../../shared/api/ApiError'
import { formatBRL } from '../../../shared/lib/money'
import { Button } from '../../../shared/ui/Button'
import { FormErrorSummary } from '../../../shared/ui/FormErrorSummary'
import { TextField } from '../../../shared/ui/TextField'
import { applyServerErrors, fieldId, toSummary } from '../formErrors'
import { useCreateOrder } from '../hooks'
import { totalCents, toPayload } from '../lib'
import { createOrderSchema, EMPTY_ITEM, type CreateOrderValues } from '../schemas'

const MAX_ITEMS = 50

const DEFAULT_VALUES: CreateOrderValues = {
  customer: { name: '', phone: '' },
  items: [EMPTY_ITEM],
  deliveryAddress: {
    street: '',
    number: '',
    complement: '',
    neighborhood: '',
    city: '',
    state: '',
    zipCode: '',
  },
}

export function NewOrderPage() {
  const navigate = useNavigate()
  const announce = useAnnounce()
  const createOrder = useCreateOrder()
  const [serverMessages, setServerMessages] = useState<string[]>([])
  const [failedSubmits, setFailedSubmits] = useState(0)
  const submitting = useRef(false)
  const pendingFocus = useRef<string | null>(null)

  const form = useForm<CreateOrderValues>({
    resolver: zodResolver(createOrderSchema),
    defaultValues: DEFAULT_VALUES,
  })
  const { errors } = form.formState
  const { fields, append, remove } = useFieldArray({ control: form.control, name: 'items' })
  const watchedItems = useWatch({ control: form.control, name: 'items' })

  // Move o foco depois que a lista de itens foi redesenhada (os ids dependem do índice).
  useEffect(() => {
    if (pendingFocus.current) {
      document.getElementById(pendingFocus.current)?.focus()
      pendingFocus.current = null
    }
  }, [fields.length])

  const addItem = () => {
    if (fields.length >= MAX_ITEMS) return
    pendingFocus.current = fieldId(`items.${fields.length}.name`)
    append(EMPTY_ITEM, { shouldFocus: false })
    announce(`Item ${fields.length + 1} adicionado`)
  }

  const removeItem = (index: number) => {
    // O foco vai para o item anterior (ou o primeiro), que sempre continua existindo.
    pendingFocus.current = fieldId(`items.${Math.max(index - 1, 0)}.name`)
    remove(index)
    announce(`Item ${index + 1} removido`)
  }

  const onValid = (values: CreateOrderValues) => {
    if (submitting.current) return
    submitting.current = true
    setServerMessages([])
    createOrder.mutate(toPayload(values), {
      onSuccess: () => {
        announce('Pedido criado')
        void navigate('/orders')
      },
      onError: (error) => {
        if (!(error instanceof ApiError)) throw error
        const unmatched = applyServerErrors(error, form.setError)
        const hasFieldErrors = error.fieldErrors.length > unmatched.length
        if (hasFieldErrors) setFailedSubmits((n) => n + 1)
        if (unmatched.length > 0) setServerMessages(unmatched)
        else if (!hasFieldErrors) setServerMessages([error.detail])
      },
      onSettled: () => {
        submitting.current = false
      },
    })
  }

  const itemsError = errors.items?.root?.message ?? errors.items?.message
  const customer = errors.customer
  const address = errors.deliveryAddress
  const total = totalCents(watchedItems)

  return (
    <>
      <h1>Novo pedido</h1>
      <form
        className="form-wide"
        noValidate
        onSubmit={(event) => {
          void form.handleSubmit(onValid, () => {
            setFailedSubmits((n) => n + 1)
          })(event)
        }}
      >
        <FormErrorSummary errors={toSummary(errors)} submitCount={failedSubmits} />
        {serverMessages.length > 0 && (
          <div role="alert" className="form-alert">
            {serverMessages.map((message) => (
              <p key={message}>{message}</p>
            ))}
          </div>
        )}

        <fieldset>
          <legend>Cliente</legend>
          <TextField
            id={fieldId('customer.name')}
            label="Nome do cliente"
            autoComplete="name"
            required
            error={customer?.name?.message}
            {...form.register('customer.name')}
          />
          <TextField
            id={fieldId('customer.phone')}
            label="Telefone"
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            required
            hint="Somente números, com DDD. Ex.: 11987654321"
            error={customer?.phone?.message}
            {...form.register('customer.phone')}
          />
        </fieldset>

        <fieldset
          id={fieldId('items')}
          tabIndex={-1}
          aria-describedby={itemsError ? 'f-items-error' : undefined}
        >
          <legend>Itens do pedido</legend>
          {itemsError && (
            <p id="f-items-error" className="field-error">
              {itemsError}
            </p>
          )}
          {fields.map((field, index) => {
            const itemErrors = errors.items?.[index]
            return (
              <fieldset key={field.id} className="item-row">
                <legend>Item {index + 1}</legend>
                <TextField
                  id={fieldId(`items.${index}.name`)}
                  label="Nome do item"
                  required
                  error={itemErrors?.name?.message}
                  {...form.register(`items.${index}.name`)}
                />
                <TextField
                  id={fieldId(`items.${index}.quantity`)}
                  label="Quantidade"
                  inputMode="numeric"
                  required
                  hint="De 1 a 99"
                  error={itemErrors?.quantity?.message}
                  {...form.register(`items.${index}.quantity`)}
                />
                <TextField
                  id={fieldId(`items.${index}.unitPrice`)}
                  label="Preço unitário (R$)"
                  inputMode="decimal"
                  required
                  hint="Ex.: 12,50"
                  error={itemErrors?.unitPrice?.message}
                  {...form.register(`items.${index}.unitPrice`)}
                />
                {fields.length > 1 && (
                  <Button
                    variant="danger"
                    onClick={() => {
                      removeItem(index)
                    }}
                  >
                    Remover item {index + 1}
                  </Button>
                )}
              </fieldset>
            )
          })}
          <Button
            variant="secondary"
            aria-disabled={fields.length >= MAX_ITEMS ? true : undefined}
            onClick={addItem}
          >
            Adicionar item
          </Button>
          {fields.length >= MAX_ITEMS && (
            <p className="field-hint">O pedido já tem o máximo de {MAX_ITEMS} itens.</p>
          )}
          <p className="order-total">
            Total do pedido: <strong>{formatBRL(total)}</strong>
          </p>
        </fieldset>

        <fieldset>
          <legend>Endereço de entrega</legend>
          <TextField
            id={fieldId('deliveryAddress.street')}
            label="Rua"
            autoComplete="address-line1"
            required
            error={address?.street?.message}
            {...form.register('deliveryAddress.street')}
          />
          <TextField
            id={fieldId('deliveryAddress.number')}
            label="Número"
            required
            error={address?.number?.message}
            {...form.register('deliveryAddress.number')}
          />
          <TextField
            id={fieldId('deliveryAddress.complement')}
            label="Complemento"
            hint="Opcional"
            autoComplete="address-line2"
            error={address?.complement?.message}
            {...form.register('deliveryAddress.complement')}
          />
          <TextField
            id={fieldId('deliveryAddress.neighborhood')}
            label="Bairro"
            required
            error={address?.neighborhood?.message}
            {...form.register('deliveryAddress.neighborhood')}
          />
          <TextField
            id={fieldId('deliveryAddress.city')}
            label="Cidade"
            autoComplete="address-level2"
            required
            error={address?.city?.message}
            {...form.register('deliveryAddress.city')}
          />
          <TextField
            id={fieldId('deliveryAddress.state')}
            label="UF"
            autoComplete="address-level1"
            autoCapitalize="characters"
            required
            hint="Duas letras maiúsculas. Ex.: SP"
            error={address?.state?.message}
            {...form.register('deliveryAddress.state')}
          />
          <TextField
            id={fieldId('deliveryAddress.zipCode')}
            label="CEP"
            inputMode="numeric"
            autoComplete="postal-code"
            required
            hint="8 dígitos, sem hífen. Ex.: 01310100"
            error={address?.zipCode?.message}
            {...form.register('deliveryAddress.zipCode')}
          />
        </fieldset>

        <div className="form-actions">
          <Button type="submit" pending={createOrder.isPending} pendingLabel="Criando pedido">
            Criar pedido
          </Button>
          <Link to="/orders">Cancelar</Link>
        </div>
      </form>
    </>
  )
}
