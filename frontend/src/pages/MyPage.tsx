import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { addressApi } from '@/entities/address/api'
import type { Address } from '@/entities/address/model'
import { addressQueries } from '@/entities/address/queries'
import { authApi } from '@/entities/auth/api'
import type { UserProfile } from '@/entities/auth/model'
import { authQueries } from '@/entities/auth/queries'
import { AddressForm } from '@/features/address/AddressForm'
import { useIsSeller } from '@/features/auth/sessionStore'
import { useLogout } from '@/features/auth/useAuthMutations'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { date, phone } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button, ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { FieldRow, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { PhoneField } from '@/shared/ui/PhoneField'
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

        {/*
          위 두 패널과 같은 열 폭에 맞춥니다. 본문 폭을 다 쓰면 버튼 둘뿐인
          패널이 화면에서 가장 큰 회색 덩어리가 됩니다. 칸이 둘인 격자에 하나만
          넣으면 첫 칸에 들어가므로 빈 칸을 따로 두지 않습니다.
        */}
        <div className="split even mt24">
          <AccountActions />
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

  const changed =
    nickname !== user.nickname || phoneNumber.replace(/\D/g, '') !== user.phone.replace(/\D/g, '')
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
          <PhoneField
            label="휴대폰 번호"
            required
            inputMode="numeric"
            placeholder="010-0000-0000"
            value={phoneNumber}
            onValueChange={setPhoneNumber}
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
      {items.length === 0 && editing !== 'new' && (
        <EmptyState>등록한 배송지가 없습니다.</EmptyState>
      )}

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

/**
 * 계정 동작.
 *
 * 로그아웃이 헤더에서 내려와 여기로 왔습니다 (S15P21B205-228). 계정을 다루는
 * 자리가 내 정보이고, 헤더에서는 하루에 한 번 쓸까 말까 한 동작이 상시 자리를
 * 차지하고 있었습니다. 헤더에서 빼기만 하고 여기를 만들지 않으면 로그아웃할
 * 방법 자체가 없어지므로 함께 옮깁니다.
 *
 * 판매자센터 입구도 여기에 둡니다. 헤더에서 내린 뒤로는 푸터 링크 하나뿐이라,
 * 판매자가 들어갈 때마다 페이지 맨 아래까지 내려가야 했습니다. 결제·관리자·
 * 판매자센터 화면은 푸터가 compact 로 바뀌어 그 링크조차 없습니다.
 *
 * 전환 여부에 따라 목적지가 갈립니다. 이미 판매자면 판매자센터로 바로 보내고,
 * 아니면 전환 화면으로 보냅니다. 전환하지 않은 사람에게 /seller 를 주면
 * RequireSeller 가드가 되돌려보낼 뿐이라 누른 쪽은 아무 일도 없어 보입니다.
 *
 * 판단은 세션의 actor.isSeller 로 합니다. 백엔드가 JWT 의 isSeller 클레임으로
 * ROLE_SELLER 를 주므로, 판매자센터에 실제로 들어갈 수 있는지와 같은 기준입니다.
 */
function AccountActions() {
  const logout = useLogout()
  const isSeller = useIsSeller()

  return (
    <Panel title="계정" description={isSeller ? '판매자센터와 로그아웃' : '판매자 전환과 로그아웃'}>
      {/* 패널이 본문 폭을 그대로 써서 버튼만 따로 묶어 좁힙니다 (.account-actions) */}
      <div className="account-actions">
        {/* 안내문구는 왼쪽 버튼에 딸린 것이라 두 버튼 아래에 한 번만 둡니다 */}
        <div className="account-row">
          <ButtonLink to={isSeller ? '/seller' : '/seller/verify'}>
            {isSeller ? '판매자센터 가기' : '판매자로 전환하기'}
          </ButtonLink>
          <Button
            variant="ghost"
            disabled={logout.isPending}
            onClick={() => {
              logout.mutate()
            }}
          >
            로그아웃
          </Button>
        </div>
        <p className="small muted mt8" style={{ marginBottom: 0 }}>
          {isSeller
            ? '등록한 상품과 공동구매, 정산을 관리합니다.'
            : '정산계좌 인증을 마치면 상품을 등록하고 공동구매를 열 수 있습니다.'}
        </p>
      </div>
    </Panel>
  )
}




