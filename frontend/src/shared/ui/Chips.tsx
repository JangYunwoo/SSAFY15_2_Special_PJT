import { cx } from '@/shared/lib/cx'

export interface ChipOption<TValue extends string> {
  value: TValue
  label: string
  /** 라벨 뒤에 붙는 건수 */
  count?: number | undefined
}

interface ChipFilterProps<TValue extends string> {
  label: string
  options: readonly ChipOption<TValue>[]
  value: TValue
  onChange: (value: TValue) => void
}

/** 카테고리·상태 필터 칩 (.chips + .chip) */
export function ChipFilter<TValue extends string>({
  label,
  options,
  value,
  onChange,
}: ChipFilterProps<TValue>) {
  return (
    <div className="chips" role="tablist" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="tab"
          aria-selected={option.value === value}
          className={cx('chip', option.value === value && 'on')}
          onClick={() => {
            onChange(option.value)
          }}
        >
          {option.label}
          {option.count !== undefined && ` ${String(option.count)}`}
        </button>
      ))}
    </div>
  )
}
