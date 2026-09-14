import { useId, type ComponentPropsWithoutRef, type ReactNode } from 'react'

import { cx } from '@/shared/lib/cx'

interface FieldShellProps {
  label: ReactNode
  required?: boolean | undefined
  /** 입력 아래 안내 문구 */
  hint?: ReactNode | undefined
  /** 검증 실패 메시지. 있으면 hint 대신 노출됩니다. */
  error?: string | undefined
  children: (props: { id: string; describedBy: string | undefined; invalid: boolean }) => ReactNode
}

/** label · 입력 · 안내/오류를 묶는 껍데기. 접근성 연결을 한곳에서 처리합니다. */
function FieldShell({ label, required, hint, error, children }: FieldShellProps) {
  const id = useId()
  const messageId = `${id}-message`
  const message = error ?? hint
  const describedBy = message === undefined || message === '' ? undefined : messageId

  return (
    <div>
      <label className="form-label" htmlFor={id}>
        {label}
        {required && <span className="req">필수</span>}
      </label>
      {children({ id, describedBy, invalid: Boolean(error) })}
      {message !== undefined && message !== '' && (
        <p
          className={cx('small', error ? 'ai-hint' : 'muted', 'mt8')}
          id={messageId}
          role={error ? 'alert' : undefined}
        >
          {message}
        </p>
      )}
    </div>
  )
}

type FieldBaseProps = Omit<FieldShellProps, 'children'>

export type TextFieldProps = FieldBaseProps & {
  /**
   * 입력 오른쪽에 붙는 버튼 (중복확인·주소검색 등).
   * label·안내 문구와 같은 줄맞춤을 쓰려고 FieldShell 안에 넣습니다.
   */
  action?: ReactNode | undefined
} & Omit<ComponentPropsWithoutRef<'input'>, 'id' | 'className'>

export function TextField({ label, required, hint, error, action, ...rest }: TextFieldProps) {
  return (
    <FieldShell label={label} required={required} hint={hint} error={error}>
      {({ id, describedBy, invalid }) => {
        const input = (
          <input
            id={id}
            className="input"
            aria-describedby={describedBy}
            aria-invalid={invalid || undefined}
            required={required}
            {...rest}
          />
        )
        if (action === undefined) return input
        return (
          <div className="input-action">
            {input}
            {action}
          </div>
        )
      }}
    </FieldShell>
  )
}

export type TextAreaFieldProps = FieldBaseProps &
  Omit<ComponentPropsWithoutRef<'textarea'>, 'id' | 'className'>

export function TextAreaField({ label, required, hint, error, ...rest }: TextAreaFieldProps) {
  return (
    <FieldShell label={label} required={required} hint={hint} error={error}>
      {({ id, describedBy, invalid }) => (
        <textarea
          id={id}
          className="input"
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          required={required}
          {...rest}
        />
      )}
    </FieldShell>
  )
}

export interface SelectOption {
  value: string
  label: string
}

export type SelectFieldProps = FieldBaseProps & {
  options: readonly SelectOption[]
  placeholder?: string | undefined
} & Omit<ComponentPropsWithoutRef<'select'>, 'id' | 'className' | 'children'>

export function SelectField({
  label,
  required,
  hint,
  error,
  options,
  placeholder,
  ...rest
}: SelectFieldProps) {
  return (
    <FieldShell label={label} required={required} hint={hint} error={error}>
      {({ id, describedBy, invalid }) => (
        <select
          id={id}
          className="input"
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          required={required}
          {...rest}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}
    </FieldShell>
  )
}

/** 가로로 나란히 놓는 입력 묶음 (.field-row) */
export function FieldRow({
  children,
  className,
}: {
  children: ReactNode
  className?: string | undefined
}) {
  return <div className={cx('field-row', className)}>{children}</div>
}
