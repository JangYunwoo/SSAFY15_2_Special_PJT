import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { addressApi } from '@/entities/address/api'
import type { Address } from '@/entities/address/model'
import { addressQueries } from '@/entities/address/queries'
import { authApi } from '@/entities/auth/api'
import type { UserProfile } from '@/entities/auth/model'
import { authQueries } from '@/entities/auth/queries'
import { AddressForm } from '@/features/address/AddressForm'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { date, phone } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { FieldRow, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

export default function MyPage() {
  useDocumentTitle('내 정보')

  const profile = useQuery(authQueries.me())
  const addresses = useQuery(addressQueries.list())

  return (
    <main className="wrap">
      <section className="section">
        <div className="sec-head">
          <div>
            <h2 className="sec-title">내 정보</h2>
            <p className="sec-sub">계정 정보와 배송지를 관리합니다.</p>
          </div>
        </div>

        {/*
          위아래로 쌓으면 두 패널이 본문 폭을 그대로 써서 표 한 줄이 필요 이상으로
          길어집니다. 라벨과 값 사이가 벌어져 짝이 잘 안 읽힙니다.
          좌우로 나란히 두면 각 표가 읽기 좋은 폭으로 줄고, 좁은 화면에서는
          .split 규칙이 알아서 한 열로 접습니다.
        */}
        <div className="split even">
          <QueryBoundary query={profile}>
            {(user) => <ProfilePanel key={user.id} user={user} />}
          </QueryBoundary>

          <Panel title="배송지" description="공동구매 상품을 받을 주소">
            <QueryBoundary query={addresses}>
              {(items) => <AddressList items={items} />}
            </QueryBoundary>
          </Panel>
        </div>
      </section>
    </main>
  )
}

function ProfilePanel({ user }: { user: UserProfile }) {
  const queryClient = useQueryClient()
  const [nickname, setNickname] = useState(user.nickname)
  const [phoneNumber, setPhoneNumber] = useState(user.phone)
  const [saved, setSaved] = useState(false)

  const save = useMutation({
    mutationFn: () => authApi.updateMe({ nickname, phone: phoneNumber.replace(/\D/g, '') }),
    onSuccess: async () => {
      setSaved(true)
      await queryClient.invalidateQueries({ queryKey: queryKeys.auth.all })
    },
  })

  const changed = nickname !== user.nickname || phoneNumber !== user.phone
  const isReady = nickname.trim() !== '' && phoneNumber.replace(/\D/g, '').length >= 10 && changed

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    setSaved(false)
    save.mutate()
  }

  return (
    <Panel title="계정 정보" description="로그인에 사용하는 정보">
      <DataRow label="이메일">{user.email}</DataRow>
      <DataRow label="가입일">{date(user.createdAt)}</DataRow>
      <DataRow label="판매자 전환">{user.isSeller ? '완료' : '미전환'}</DataRow>

      <form className="mt16" onSubmit={submit} noValidate>
        <FieldRow>
          <TextField
            label="닉네임"
            required
            value={nickname}
            onChange={(event) => {
              setNickname(event.target.value)
            }}
          />
          <TextField
            label="휴대폰 번호"
            required
            inputMode="numeric"
            placeholder="010-0000-0000"
            value={phoneNumber}
            onChange={(event) => {
              setPhoneNumber(event.target.value)
            }}
          />
        </FieldRow>

        {save.isError && (
          <p className="ai-hint mt16" role="alert">
            {toMessage(save.error)}
          </p>
        )}
        {saved && !changed && (
          <p className="ai-hint mt16" role="status">
            저장했습니다.
          </p>
        )}

        <Button type="submit" block className="mt16" disabled={!isReady || save.isPending}>
          {save.isPending ? '저장 중…' : '내 정보 저장'}
        </Button>
      </form>
    </Panel>
  )
}

function AddressList({ items }: { items: readonly Address[] }) {
  const queryClient = useQueryClient()
  /** 'new' 면 추가 폼, 그 외에는 수정 중인 배송지 id */
  const [editing, setEditing] = useState<string | null>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.addresses.all })
  const close = () => {
    setEditing(null)
  }

  const setDefault = useMutation({
    mutationFn: (addressId: string) => addressApi.update(addressId, { isDefault: true }),
    onSuccess: refresh,
  })

  const remove = useMutation({
    mutationFn: (addressId: string) => addressApi.remove(addressId),
    onSuccess: refresh,
  })

  const busy = setDefault.isPending || remove.isPending
  const error = setDefault.error ?? remove.error

  const confirmRemove = (address: Address) => {
    // 되돌릴 수 없어 한 번 더 묻습니다.
    if (window.confirm(`${address.recipientName} 배송지를 삭제할까요? 되돌릴 수 없습니다.`)) {
      remove.mutate(address.id)
    }
  }

  return (
    <>
      {items.length === 0 && editing !== 'new' && <EmptyState>등록한 배송지가 없습니다.</EmptyState>}

      {items.map((address) => (
        <div key={address.id} className="order">
          {editing === address.id ? (
            <div className="order-detail" style={{ display: 'block', padding: '20px 24px' }}>
              <AddressForm address={address} onDone={close} onCancel={close} />
            </div>
          ) : (
            <>
              <div className="order-row" style={{ cursor: 'default' }}>
                <div className="info">
                  <span className="name">
                    {address.recipientName}
                    {address.isDefault && <StateChip tone="live">기본</StateChip>}
                  </span>
                  <span className="small muted">
                    {phone(address.recipientPhone)} · ({address.zipcode}) {address.address1}{' '}
                    {address.address2}
                  </span>
                  {address.deliveryRequest !== null && (
                    <span className="small muted">요청: {address.deliveryRequest}</span>
                  )}
                </div>
              </div>
              <div className="order-detail" style={{ display: 'block', paddingTop: 0 }}>
                <div className="order-actions" style={{ marginTop: 0 }}>
                  {!address.isDefault && (
                    <Button
                      variant="ghost"
                      disabled={busy}
                      onClick={() => {
                        setDefault.mutate(address.id)
                      }}
                    >
                      기본으로 지정
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    disabled={busy}
                    onClick={() => {
                      setEditing(address.id)
                    }}
                  >
                    수정
                  </Button>
                  <Button
                    variant="ghost"
                    disabled={busy}
                    onClick={() => {
                      confirmRemove(address)
                    }}
                  >
                    삭제
                  </Button>
                </div>
              </div>
            </>
          )}
        </div>
      ))}

      {error !== null && (
        <p className="ai-hint mt16" role="alert">
          {toMessage(error)}
        </p>
      )}

      {editing === 'new' ? (
        <div className="mt16">
          <AddressForm onDone={close} onCancel={close} />
        </div>
      ) : (
        <Button
          variant="ghost"
          block
          className="mt16"
          onClick={() => {
            setEditing('new')
          }}
        >
          새 배송지 추가
        </Button>
      )}
    </>
  )
}
