import type { ReactNode } from 'react'

import { PAYMENT_METHOD_LABEL, type PaymentMethod } from '@/entities/model'
import { cx } from '@/shared/lib/cx'

interface Option {
  method: PaymentMethod
  note: string
  body: ReactNode
}

const NOTES: Record<PaymentMethod, string> = {
  CARD: '국내 모든 카드 · 일시불',
  KAKAOPAY: '간편결제',
  CRYPTO: '실시간 환율 · KRW 기준 기록',
}

interface PaymentMethodPickerProps {
  value: PaymentMethod
  onChange: (method: PaymentMethod) => void
  /** 각 수단을 펼쳤을 때 보여줄 입력 영역 */
  bodies: Record<PaymentMethod, ReactNode>
}

/** 세로로 쌓이고 선택 시 펼쳐지는 결제수단 목록 (.pay-list) */
export function PaymentMethodPicker({ value, onChange, bodies }: PaymentMethodPickerProps) {
  const options: Option[] = (['CARD', 'KAKAOPAY', 'CRYPTO'] as const).map((method) => ({
    method,
    note: NOTES[method],
    body: bodies[method],
  }))

  return (
    <div className="pay-list" role="radiogroup" aria-label="결제수단">
      {options.map((option) => {
        const selected = option.method === value
        return (
          <div key={option.method} className={cx('pay-option', selected && 'on')}>
            <button
              type="button"
              className="pay-tab"
              role="radio"
              aria-checked={selected}
              onClick={() => {
                onChange(option.method)
              }}
            >
              <span className="pay-radio" aria-hidden="true" />
              <span className="pay-name">
                {PAYMENT_METHOD_LABEL[option.method]}
                {option.method === 'CARD' ? ' 결제' : ''}
              </span>
              <span className="pay-note">{option.note}</span>
            </button>
            <div className={cx('pay-body', selected && 'on')} hidden={!selected}>
              {option.body}
            </div>
          </div>
        )
      })}
    </div>
  )
}
