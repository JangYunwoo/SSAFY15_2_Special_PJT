import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { addressApi } from '@/entities/address/api'
import { DELIVERY_REQUESTS, type Address, type AddressInput } from '@/entities/address/model'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { Button } from '@/shared/ui/Button'
import { FieldRow, SelectField, TextField } from '@/shared/ui/Field'

interface FormState {
  recipientName: string
  recipientPhone: string
  zipcode: string
  address1: string
  address2: string
  deliveryRequest: string
}

const EMPTY: FormState = {
  recipientName: '',
  recipientPhone: '',
  zipcode: '',
  address1: '',
  address2: '',
  deliveryRequest: '',
}

function toFormState(address: Address): FormState {
  return {
    recipientName: address.recipientName,
    recipientPhone: address.recipientPhone,
    zipcode: address.zipcode,
    address1: address.address1,
    address2: address.address2,
    deliveryRequest: address.deliveryRequest ?? '',
  }
}

interface AddressFormProps {
  /** 넘기면 수정, 없으면 새 배송지 등록입니다. */
  address?: Address | undefined
  onDone: () => void
  onCancel: () => void
}

/** 배송지 등록·수정 폼 (USR-04 / USR-05) */
export function AddressForm({ address, onDone, onCancel }: AddressFormProps) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(() => (address ? toFormState(address) : EMPTY))

  const set =
    (key: keyof FormState) =>
    (event: { target: { value: string } }): void => {
      setForm((current) => ({ ...current, [key]: event.target.value }))
    }

  const save = useMutation({
    mutationFn: () => {
      const body: AddressInput = {
        recipientName: form.recipientName,
        recipientPhone: form.recipientPhone.replace(/\D/g, ''),
        zipcode: form.zipcode,
        address1: form.address1,
        address2: form.address2,
        deliveryRequest: form.deliveryRequest || null,
        // 기본 배송지 지정은 목록에서 따로 하므로 여기서는 기존 값을 지킵니다.
        isDefault: address?.isDefault ?? false,
      }
      return address ? addressApi.update(address.id, body) : addressApi.create(body)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.addresses.all })
      onDone()
    },
  })

  const isReady =
    form.recipientName.trim() !== '' &&
    form.recipientPhone.replace(/\D/g, '').length >= 10 &&
    form.zipcode.length === 5 &&
    form.address1.trim() !== ''

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    save.mutate()
  }

  return (
    <form onSubmit={submit} noValidate>
      <FieldRow>
        <TextField
          label="받는 사람"
          required
          value={form.recipientName}
          onChange={set('recipientName')}
        />
        <TextField
          label="받는 분 연락처"
          required
          inputMode="numeric"
          placeholder="010-0000-0000"
          value={form.recipientPhone}
          onChange={set('recipientPhone')}
        />
      </FieldRow>

      <TextField
        label="우편번호"
        required
        inputMode="numeric"
        maxLength={5}
        value={form.zipcode}
        onChange={set('zipcode')}
      />
      <TextField label="기본 주소" required value={form.address1} onChange={set('address1')} />
      <TextField label="상세 주소" value={form.address2} onChange={set('address2')} />

      <SelectField
        label="배송 요청사항"
        placeholder="선택 없음"
        value={form.deliveryRequest}
        onChange={set('deliveryRequest')}
        options={DELIVERY_REQUESTS.map((text) => ({ value: text, label: text }))}
      />

      {save.isError && (
        <p className="ai-hint mt16" role="alert">
          {toMessage(save.error)}
        </p>
      )}

      <div className="order-actions">
        <Button type="submit" disabled={!isReady || save.isPending}>
          {save.isPending ? '저장 중…' : address ? '수정 저장' : '배송지 추가'}
        </Button>
        <Button variant="ghost" onClick={onCancel} disabled={save.isPending}>
          취소
        </Button>
      </div>
    </form>
  )
}
