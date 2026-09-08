import { FieldRow, TextField } from '@/shared/ui/Field'

import { formatCardNumber, formatExpiry, type CardInput } from './cardInput'

interface CardFieldsProps {
  value: CardInput
  onChange: (value: CardInput) => void
}

/** 카드 정보 입력. 값은 승인 요청에만 쓰이고 저장되지 않습니다. */
export function CardFields({ value, onChange }: CardFieldsProps) {
  const patch = (next: Partial<CardInput>) => {
    onChange({ ...value, ...next })
  }

  return (
    <>
      <TextField
        label="카드 정보"
        placeholder="카드번호"
        inputMode="numeric"
        autoComplete="cc-number"
        maxLength={19}
        value={value.cardNumber}
        onChange={(event) => {
          patch({ cardNumber: formatCardNumber(event.target.value) })
        }}
      />
      <FieldRow className="mt8">
        <TextField
          label="유효기간"
          placeholder="MM / YY"
          inputMode="numeric"
          autoComplete="cc-exp"
          maxLength={7}
          value={value.expiry}
          onChange={(event) => {
            patch({ expiry: formatExpiry(event.target.value) })
          }}
        />
        <TextField
          label="CVC"
          placeholder="CVC"
          inputMode="numeric"
          autoComplete="cc-csc"
          maxLength={4}
          value={value.cvc}
          onChange={(event) => {
            patch({ cvc: event.target.value.replace(/\D/g, '') })
          }}
        />
      </FieldRow>
    </>
  )
}
