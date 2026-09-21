import { useLayoutEffect, useRef } from 'react'

import { formatPhoneInput, phoneCaret, phoneDigits } from '@/shared/lib/phoneInput'

import { TextField, type TextFieldProps } from './Field'

type Props = Omit<TextFieldProps, 'value' | 'onChange' | 'onKeyDown' | 'type' | 'maxLength'> & {
  value: string
  onValueChange: (digits: string) => void
}

/** 기존 입력 디자인·레이블을 그대로 사용하고 국내 전화번호의 표시만 바꾼다. */
export function PhoneField({ value, onValueChange, ...props }: Props) {
  const pending = useRef<{ input: HTMLInputElement; digitsBeforeCaret: number } | null>(null)
  const formatted = formatPhoneInput(value)

  useLayoutEffect(() => {
    const position = pending.current
    if (position !== null) {
      const caret = phoneCaret(formatted, position.digitsBeforeCaret)
      position.input.setSelectionRange(caret, caret)
      pending.current = null
    }
  })

  return (
    <TextField
      {...props}
      type="tel"
      inputMode="tel"
      autoComplete={props.autoComplete ?? 'tel-national'}
      value={formatted}
      onChange={(event) => {
        const input = event.currentTarget
        const next = phoneDigits(input.value)
        const count = phoneDigits(input.value.slice(0, input.selectionStart ?? input.value.length)).length
        pending.current = { input, digitsBeforeCaret: count }
        // 숫자가 변하지 않는 붙여넣기에서도 화면과 커서를 즉시 정규화한다.
        input.value = formatPhoneInput(next)
        const caret = phoneCaret(input.value, count)
        input.setSelectionRange(caret, caret)
        onValueChange(next)
      }}
      onKeyDown={(event) => {
        const input = event.currentTarget
        const start = input.selectionStart ?? 0
        if (input.selectionEnd !== start) return
        const backward = event.key === 'Backspace' && formatted[start - 1] === '-'
        const forward = event.key === 'Delete' && formatted[start] === '-'
        if (!backward && !forward) return
        event.preventDefault()
        const before = phoneDigits(formatted.slice(0, start)).length
        const remove = backward ? before - 1 : before
        const digits = phoneDigits(value)
        pending.current = { input, digitsBeforeCaret: backward ? remove : before }
        onValueChange(digits.slice(0, remove) + digits.slice(remove + 1))
      }}
    />
  )
}
