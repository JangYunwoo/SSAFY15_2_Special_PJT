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
      {/* 제목·패널·로그아웃이 같은 가운데 기둥에 들어갑니다 (.stack-page) */}
      <section className="section stack-page">
        <div className="sec-head">
          <div>
            <h2 className="sec-title">내 정보</h2>
            <p className="sec-sub">계정 정보와 배송지를 관리합니다.</p>
          </div>
        </div>

        {/*
          패널을 세로로 쌓습니다.

          두 칸으로 나누면 세 번째 패널이 다음 줄에 혼자 남아 오른쪽 절반이
          비어 보였습니다. 다만 본문 폭을 그대로 쓰면 DataRow 의 라벨과 값이
          양 끝으로 멀어져 짝이 잘 안 읽히므로, .panel-stack 이 폭을 잡아 둡니다.
        */}
        <div className="panel-stack">
          <QueryBoundary query={profile}>
            {(user) => <ProfilePanel key={user.id} user={user} />}
          </QueryBoundary>

          <Panel title="배송지" description="공동구매 상품을 받을 주소">
            <QueryBoundary query={addresses}>
              {(items) => <AddressList items={items} />}
            </QueryBoundary>
          </Panel>

          <SellerEntry />
        </div>

        {/*
          로그아웃은 맨 아래 따로 둡니다. 판매자센터 버튼 옆에 나란히 두었더니
          앞으로 가는 동작과 세션을 끝내는 동작이 같은 무게로 붙어, 어느 쪽이
          주된 동작인지 흐려지고 누르려던 것과 다른 쪽을 누르기 쉬웠습니다.
        */}
        <AccountExit />
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
 * 판매자센터 입구.
 *
 * 헤더에서 내린 뒤로는 푸터 링크 하나뿐이라, 판매자가 들어갈 때마다 페이지 맨
 * 아래까지 내려가야 했습니다. 결제·관리자·판매자센터 화면은 푸터가 compact 로
 * 바뀌어 그 링크조차 없습니다.
 *
 * 전환 여부에 따라 목적지가 갈립니다. 이미 판매자면 판매자센터로 바로 보내고,
 * 아니면 전환 화면으로 보냅니다. 전환하지 않은 사람에게 /seller 를 주면
 * RequireSeller 가드가 되돌려보낼 뿐이라 누른 쪽은 아무 일도 없어 보입니다.
 *
 * 판단은 세션의 actor.isSeller 로 합니다. 백엔드가 JWT 의 isSeller 클레임으로
 * ROLE_SELLER 를 주므로, 판매자센터에 실제로 들어갈 수 있는지와 같은 기준입니다.
 *
 * 로그아웃은 여기 두지 않습니다 ({@link LogoutRow}).
 */
function SellerEntry() {
  const isSeller = useIsSeller()

  return (
    <Panel
      title="판매자"
      description={isSeller ? '내가 연 공동구매를 관리합니다' : '상품을 팔고 싶다면'}
    >
      {/* 패널이 본문 폭을 그대로 써서 버튼만 따로 묶어 좁힙니다 (.account-actions) */}
      <div className="account-actions">
        <ButtonLink block to={isSeller ? '/seller' : '/seller/verify'}>
          {isSeller ? '판매자센터 가기' : '판매자로 전환하기'}
        </ButtonLink>
        <p className="small muted mt8" style={{ marginBottom: 0 }}>
          {isSeller
            ? '등록한 상품과 공동구매, 정산을 관리합니다.'
            : '정산계좌 인증을 마치면 상품을 등록하고 공동구매를 열 수 있습니다.'}
        </p>
      </div>
    </Panel>
  )
}

/**
 * 계정을 끝내는 동작들.
 *
 * 로그아웃이 헤더에서 내려와 내 정보로 온 것은 그대로입니다 (S15P21B205-228).
 * 계정을 다루는 자리가 여기이고, 헤더에서는 하루에 한 번 쓸까 말까 한 동작이
 * 상시 자리를 차지하고 있었습니다.
 *
 * 다만 판매자센터 버튼 옆에 나란히 두지는 않습니다. 하나는 앞으로 가는 동작이고
 * 다른 하나는 세션을 끝내는 동작이라 성격이 다른데, 같은 줄에 같은 크기로 두면
 * 어느 쪽이 주된 동작인지 흐려집니다. 맨 아래에 따로, 낮은 강조로 둡니다.
 *
 * <p><b>회원탈퇴 버튼은 아무 동작도 하지 않습니다.</b> 서버에 탈퇴 API 가 없습니다
 * (/users/me 는 GET·PATCH 뿐이고 users.status 의 WITHDRAWN 은 코드에서 쓰이지
 * 않습니다). 화면 자리만 먼저 잡아 둔 상태입니다.
 *
 * <p>탈퇴를 실제로 붙일 때는 진행 중인 거래(참여·환불·정산·반품)를 먼저 막아야
 * 하고, 거래 기록은 보존 의무가 있어 행을 지우는 대신 status 를 WITHDRAWN 으로
 * 바꾸고 개인정보만 비우는 방식이어야 합니다.
 */
function AccountExit() {
  const logout = useLogout()

  return (
    <div className="logout-row">
      <div className="logout-actions">
        <Button
          variant="ghost"
          disabled={logout.isPending}
          onClick={() => {
            logout.mutate()
          }}
        >
          {logout.isPending ? '로그아웃 중…' : '로그아웃'}
        </Button>
        {/* 자리만 잡아 둔 버튼입니다. onClick 이 없어 눌러도 아무 일도 하지 않습니다 */}
        <Button variant="ghost">회원탈퇴</Button>
      </div>
    </div>
  )
}




