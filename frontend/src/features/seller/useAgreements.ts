import { useCallback, useMemo, useState } from 'react'

export interface AgreementState {
  checked: readonly boolean[]
  allChecked: boolean
  toggle: (index: number) => void
  toggleAll: () => void
}

/** 필수 약관 체크 상태. "모두 동의" 와 개별 항목을 함께 관리합니다. */
export function useAgreements(count: number): AgreementState {
  const [checked, setChecked] = useState<boolean[]>(() =>
    Array.from({ length: count }, () => false),
  )

  const allChecked = useMemo(() => checked.every(Boolean), [checked])

  const toggle = useCallback((index: number) => {
    setChecked((current) => current.map((value, at) => (at === index ? !value : value)))
  }, [])

  const toggleAll = useCallback(() => {
    setChecked((current) => {
      const next = !current.every(Boolean)
      return current.map(() => next)
    })
  }, [])

  return { checked, allChecked, toggle, toggleAll }
}
