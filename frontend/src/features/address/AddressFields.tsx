import { useEffect, useId, useRef, useState } from 'react'

import { Button } from '@/shared/ui/Button'
import { TextField } from '@/shared/ui/Field'

import { loadPostcode, selectedAddress, type SearchAddress } from './postcode'
import './addressSearch.css'

interface Value extends SearchAddress { address2: string }
interface Props {
  value: Value
  onChange: (value: Value) => void
  prefix?: string
  errors?: Partial<Record<keyof Value, string | undefined>>
}

export function AddressFields({ value, onChange, prefix = '', errors }: Props) {
  const [open, setOpen] = useState(false)
  const fields = useRef<HTMLDivElement>(null)
  const focusDetails = useRef(false)
  useEffect(() => {
    // 삭제된 dialog의 effect 정리(기본 포커스 복귀)가 끝난 뒤 실행한다.
    if (!open && focusDetails.current) {
      focusDetails.current = false
      fields.current?.querySelector<HTMLInputElement>('[data-address-detail]')?.focus()
    }
  }, [open])
  return (
    <div ref={fields}>
      <TextField
        label={`${prefix}우편번호`} required readOnly value={value.zipcode}
        error={errors?.zipcode} placeholder="주소 검색으로 입력"
        action={<Button variant="ghost" onClick={() => { setOpen(true) }}>주소 검색</Button>}
      />
      <TextField label={`${prefix}기본 주소`} required readOnly value={value.address1} error={errors?.address1} />
      <TextField
        label={`${prefix}상세 주소`} value={value.address2} error={errors?.address2}
        autoComplete="address-line2" data-address-detail
        onChange={(event) => { onChange({ ...value, address2: event.target.value }) }}
      />
      {open && (
        <AddressSearchPopup
          onClose={() => { setOpen(false) }}
          onSelect={(address) => {
            const changed = address.zipcode !== value.zipcode || address.address1 !== value.address1
            onChange({ ...address, address2: changed ? '' : value.address2 })
            focusDetails.current = true
            setOpen(false)
          }}
        />
      )}
    </div>
  )
}

function AddressSearchPopup({ onSelect, onClose }: {
  onSelect: (address: SearchAddress) => void
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const container = useRef<HTMLDivElement>(null)
  const titleId = useId()
  const [error, setError] = useState<string | null>(null)
  const [ready, setReady] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    const popup = dialog.current
    const target = container.current
    if (!popup || !target) return
    if (!popup.open) popup.showModal()
    void loadPostcode().then((Postcode) => {
      if (!active) return
      new Postcode({
        width: '100%', height: '100%',
        oncomplete: (result) => {
          if (!active) return
          try { onSelect(selectedAddress(result)) }
          catch (cause) { setError(cause instanceof Error ? cause.message : '주소를 다시 선택해 주세요.') }
        },
      }).embed(target)
      setReady(true)
    }).catch((cause: unknown) => {
      if (active) setError(cause instanceof Error ? cause.message : '주소 검색을 불러오지 못했습니다.')
    })
    return () => {
      active = false
      target.replaceChildren()
      if (popup.open) popup.close()
    }
  }, [onSelect, attempt])

  return (
    <dialog ref={dialog} className="postcode-dialog" aria-labelledby={titleId} onCancel={onClose}>
      <div className="sec-head">
        <h2 id={titleId} className="sec-title">주소 검색</h2>
        <Button variant="ghost" onClick={onClose}>닫기</Button>
      </div>
      {!ready && !error && <p role="status">주소 검색을 불러오는 중…</p>}
      {error && <div role="alert"><p className="ai-hint">{error}</p><Button variant="ghost" onClick={() => {
        setError(null); setReady(false); setAttempt((count) => count + 1)
      }}>다시 시도</Button></div>}
      <div ref={container} className="postcode-search-frame" />
    </dialog>
  )
}
