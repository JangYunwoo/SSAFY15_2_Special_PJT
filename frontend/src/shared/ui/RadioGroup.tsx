import { useId, type ReactNode } from 'react'

import { cx } from '@/shared/lib/cx'

export interface RadioOption<TValue extends string> {
  value: TValue
  label: ReactNode
}

interface RadioGroupProps<TValue extends string> {
  legend: string
  options: readonly RadioOption<TValue>[]
  value: TValue
  onChange: (value: TValue) => void
  /** legend 를 시각적으로 감출지 여부 */
  hideLegend?: boolean | undefined
}

/** 목업의 .choice-row + .pick — 라디오 카드 묶음 */
export function RadioGroup<TValue extends string>({
  legend,
  options,
  value,
  onChange,
  hideLegend = true,
}: RadioGroupProps<TValue>) {
  const name = useId()

  return (
    <fieldset className="choice-row">
      <legend className={hideLegend ? 'sr-only' : 'form-label'}>{legend}</legend>
      {options.map((option) => (
        <label key={option.value} className={cx('pick', option.value === value && 'on')}>
          <input
            type="radio"
            name={name}
            value={option.value}
            checked={option.value === value}
            onChange={() => {
              onChange(option.value)
            }}
          />
          {option.label}
        </label>
      ))}
    </fieldset>
  )
}
