import { useState, type ChangeEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useMutation } from '@tanstack/react-query'

import { authApi } from '@/entities/auth/api'
import type { SellerAccountRequestResult } from '@/entities/auth/model'
import { useIsSeller, useSessionStore } from '@/features/auth/sessionStore'
import { SiteFooter } from '@/app/layouts/SiteFooter'
import { toMessage } from '@/shared/api/errors'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { BrandMark } from '@/shared/ui/BrandMark'
import { Button } from '@/shared/ui/Button'
import { FieldRow, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { Timeline, type TimelineStep } from '@/shared/ui/Timeline'
import { PhoneField } from '@/shared/ui/PhoneField'
import { AddressFields } from '@/features/address/AddressFields'

type Step = 'account' | 'profile'

/**
 * 진행 표시 (S15P21B205-231).
 *
 * "완료"는 끝점으로 그리되 단계 수에는 넣지 않습니다. 사용자가 실제로 해야 하는
 * 일은 계좌 인증과 정보 입력 둘뿐인데 "3단계 중 1단계"라고 적으면 할 일이 셋으로
 * 읽혀, 인증이 많아 보이지 않게 하려던 의도와 반대로 갑니다.
 */
const TOTAL_STEPS = 2

function stepTimeline(step: Step): TimelineStep[] {
  const current = step === 'account' ? 0 : 1
  return ['정산계좌 인증', '판매자 정보', '완료'].map((label, index) => ({
    label,
    state: index < current ? 'done' : index === current ? 'now' : 'todo',
  }))
}

/**
 * 판매자 전환 (SEL-03~05).
 *
 * 2단계로 진행합니다: 정산계좌 1원 인증(실제 SSAFY 금융망) → 판매자 정보 등록.
 * 마지막 단계가 성공하면 백엔드가 isSeller=true 인 새 토큰을 내려주며, 그 토큰으로
 * 세션을 갱신하고 판매자센터로 들어갑니다.
 *
 * 휴대폰 인증은 SSAFY에 API가 없고 은행 업무도 아니라 제거했습니다. 판매자에게 실제로
 * 필요한 것은 정산 받을 계좌가 본인 것인지 확인하는 1원 인증입니다. 데모 편의로 1원
 * 인증코드는 서버가 응답에 함께 내려주므로 자동으로 채워 넣습니다.
 */
export default function SellerVerifyPage() {
  useDocumentTitle('판매자 전환')

  const isSeller = useIsSeller()
  const navigate = useNavigate()
  const signIn = useSessionStore((state) => state.signIn)

  const [step, setStep] = useState<Step>('account')

  const [account, setAccount] = useState<SellerAccountRequestResult | null>(null)
  const [authCode, setAuthCode] = useState('')

  const [businessName, setBusinessName] = useState('')
  const [businessPhone, setBusinessPhone] = useState('')
  const [accountHolder, setAccountHolder] = useState('')
  const [zipcode, setZipcode] = useState('')
  const [address1, setAddress1] = useState('')
  const [address2, setAddress2] = useState('')

  const requestAccount = useMutation({
    mutationFn: () => authApi.requestSellerAccount(),
    onSuccess: (result) => {
      setAccount(result)
      setAuthCode(result.devAuthCode)
    },
  })
  const confirmAccount = useMutation({
    mutationFn: () => authApi.confirmSellerAccount(account?.accountNo ?? '', authCode),
    onSuccess: () => {
      setStep('profile')
    },
  })
  const createProfile = useMutation({
    mutationFn: () =>
      authApi.createSellerProfile({
        businessName,
        businessPhone: businessPhone.replace(/\D/g, ''),
        accountHolder,
        returnAddress: { zipcode, address1, address2 },
      }),
    onSuccess: async (tokens) => {
      signIn(tokens)
      await navigate('/seller', { replace: true })
    },
  })

  // 이미 전환된 계정이 주소로 들어오면 판매자센터로 보냅니다.
  if (isSeller) return <Navigate to="/seller" replace />

  const on = (setter: (v: string) => void) => (e: ChangeEvent<HTMLInputElement>) => {
    setter(e.target.value)
  }
  const errorOf = (m: { isError: boolean; error: unknown }) =>
    m.isError ? toMessage(m.error) : undefined

  return (
    <>
      <header className="gnb">
        <div className="inner">
          {/* 로고는 홈으로 돌아가는 유일한 출구입니다. span 이라 눌러도 아무 일이 없었습니다 (S15P21B205-232) */}
          <Link className="brand" to="/">
            <BrandMark />
          </Link>
          <span className="badge">판매자센터</span>
        </div>
      </header>

      <main className="wrap">
        <section className="section" style={{ paddingTop: 40 }}>
          <div className="sec-head">
            <div>
              <h2 className="sec-title">판매자 전환</h2>
              <p className="sec-sub">
                정산계좌 1원 인증 후 판매자 정보를 등록하면 판매자센터를 이용할 수 있습니다.
              </p>
            </div>
          </div>

          <div className="verify-steps">
            <p className="verify-steps-count">
              {TOTAL_STEPS}단계 중 <b>{step === 'account' ? 1 : 2}단계</b>
            </p>
            <Timeline steps={stepTimeline(step)} />
          </div>

          <div className="split">
            <div>
              {/* 1단계: 정산계좌 1원 인증 (실제 SSAFY) */}
              <Panel
                title="1. 정산계좌 1원 인증"
                description={
                  step === 'account'
                    ? 'SSAFY 금융망으로 1원을 보내 예금주를 확인합니다'
                    : '완료됨 ✓'
                }
              >
                {step === 'account' ? (
                  <>
                    {account === null ? (
                      requestAccount.isPending ? (
                        /*
                          1원 송금은 SSAFY 금융망을 실제로 부르는 비동기 작업입니다.
                          버튼 글자만 "…" 로 바꿔서는 눌린 건지 멈춘 건지 알 수 없어,
                          지금 무엇을 하는 중이고 얼마나 걸리는지 적어 둡니다.
                        */
                        <div role="status" aria-live="polite">
                          <p className="wait-title">정산계좌를 확인하고 1원을 보내는 중입니다</p>
                          <p className="small muted" style={{ marginBottom: 0 }}>
                            SSAFY 금융망에 실제로 요청을 보냅니다. 보통 몇 초면 끝나지만 은행 응답이
                            늦으면 30초쯤 걸릴 수 있습니다. 그동안 이 창을 닫지 마세요.
                          </p>
                          <div className="wait-bar" aria-hidden="true">
                            <span />
                          </div>
                        </div>
                      ) : requestAccount.isError ? (
                        <>
                          <p className="ai-hint" role="alert">
                            {toMessage(requestAccount.error)}
                          </p>
                          <Button
                            block
                            className="mt16"
                            onClick={() => {
                              requestAccount.mutate()
                            }}
                          >
                            다시 시도
                          </Button>
                        </>
                      ) : (
                        <Button
                          block
                          onClick={() => {
                            requestAccount.mutate()
                          }}
                        >
                          정산계좌 만들고 1원 인증 받기
                        </Button>
                      )
                    ) : (
                      <>
                        {/*
                          서버 메시지(account.message)는 쓰지 않습니다. 앞 문장과 같은 말을
                          한 번 더 하고, 끝에 "(데모: 자동 조회됨)" 이 붙어 있습니다.
                        */}
                        <p className="small muted">
                          정산계좌 <b>{account.accountNo}</b> 로 1원을 보냈습니다. 입금 내역의
                          인증코드를 입력하세요.
                        </p>
                        <TextField
                          label="1원 인증코드"
                          value={authCode}
                          onChange={on(setAuthCode)}
                          error={errorOf(confirmAccount)}
                          action={
                            <Button
                              onClick={() => {
                                confirmAccount.mutate()
                              }}
                              disabled={confirmAccount.isPending || authCode.trim() === ''}
                            >
                              {confirmAccount.isPending ? '확인 중…' : '확인'}
                            </Button>
                          }
                        />
                      </>
                    )}
                  </>
                ) : (
                  <p className="small muted">정산계좌 1원 인증을 완료했습니다.</p>
                )}
              </Panel>

              {/* 2단계: 판매자 정보 */}
              {step === 'profile' && (
                <Panel
                  className="mt16"
                  title="2. 판매자 정보"
                  description="상호와 반품 수취 주소를 등록합니다"
                >
                  <TextField
                    label="상호명"
                    required
                    value={businessName}
                    onChange={on(setBusinessName)}
                  />
                  <FieldRow className="mt8">
                    <PhoneField
                      label="대표 연락처"
                      inputMode="numeric"
                      placeholder="010-0000-0000"
                      value={businessPhone}
                      onValueChange={setBusinessPhone}
                    />
                    <TextField
                      label="예금주"
                      required
                      value={accountHolder}
                      onChange={on(setAccountHolder)}
                    />
                  </FieldRow>
                  <AddressFields
                    prefix="반품 "
                    value={{ zipcode, address1, address2 }}
                    onChange={(next) => {
                      setZipcode(next.zipcode)
                      setAddress1(next.address1)
                      setAddress2(next.address2)
                    }}
                  />
                  {createProfile.isError && (
                    <p className="ai-hint mt16" role="alert">
                      {toMessage(createProfile.error)}
                    </p>
                  )}
                  <Button
                    block
                    className="mt16"
                    onClick={() => {
                      createProfile.mutate()
                    }}
                    disabled={
                      createProfile.isPending ||
                      businessName.trim() === '' ||
                      accountHolder.trim() === '' ||
                      zipcode.trim() === '' ||
                      address1.trim() === ''
                    }
                  >
                    {createProfile.isPending ? '전환 중…' : '판매자로 전환하기'}
                  </Button>
                </Panel>
              )}
            </div>

            <aside>
              <Panel title="안내" description="전자상거래법 제10조">
                <p className="small muted" style={{ marginBottom: 0 }}>
                  정산계좌 1원 인증은 SSAFY 금융망으로 실제 1원을 보내 예금주를 확인합니다. 카드가
                  없으면 인증 과정에서 정산계좌가 자동으로 개설됩니다.
                </p>
              </Panel>
            </aside>
          </div>
        </section>
      </main>

      <SiteFooter compact />
    </>
  )
}

