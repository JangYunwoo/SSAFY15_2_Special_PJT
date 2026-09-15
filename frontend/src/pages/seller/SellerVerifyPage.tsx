import { useState, type ChangeEvent } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { useMutation } from '@tanstack/react-query'

import { authApi } from '@/entities/auth/api'
import type { SellerAccountRequestResult, SellerPhoneRequestResult } from '@/entities/auth/model'
import { useIsSeller, useSessionStore } from '@/features/auth/sessionStore'
import { SiteFooter } from '@/app/layouts/SiteFooter'
import { toMessage } from '@/shared/api/errors'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { FieldRow, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'

type Step = 'phone' | 'account' | 'profile'

/**
 * 판매자 전환 (SEL-01~05).
 *
 * 3단계로 진행합니다: 휴대폰 인증(목업) → 정산계좌 1원 인증(실제 SSAFY 금융망) →
 * 판매자 정보 등록. 마지막 단계가 성공하면 백엔드가 isSeller=true 인 새 토큰을
 * 내려주며, 그 토큰으로 세션을 갱신하고 판매자센터로 들어갑니다.
 *
 * 데모 편의: 휴대폰 인증코드와 1원 인증코드를 서버가 응답으로 함께 내려주므로
 * (문자 발송 사업자·실제 은행앱이 없으니) 화면에 표시하고 자동 채워 넣습니다.
 */
export default function SellerVerifyPage() {
  useDocumentTitle('판매자 전환')

  const isSeller = useIsSeller()
  const navigate = useNavigate()
  const signIn = useSessionStore((state) => state.signIn)

  const [step, setStep] = useState<Step>('phone')
  const [phone, setPhone] = useState('')
  const [phoneCode, setPhoneCode] = useState('')
  const [phoneHint, setPhoneHint] = useState<SellerPhoneRequestResult | null>(null)

  const [account, setAccount] = useState<SellerAccountRequestResult | null>(null)
  const [authCode, setAuthCode] = useState('')

  const [businessName, setBusinessName] = useState('')
  const [businessPhone, setBusinessPhone] = useState('')
  const [accountHolder, setAccountHolder] = useState('')
  const [zipcode, setZipcode] = useState('')
  const [address1, setAddress1] = useState('')
  const [address2, setAddress2] = useState('')

  const requestPhone = useMutation({
    mutationFn: () => authApi.requestSellerPhone(phone.replace(/\D/g, '')),
    onSuccess: (result) => {
      setPhoneHint(result)
      setPhoneCode(result.devCode)
    },
  })
  const confirmPhone = useMutation({
    mutationFn: () => authApi.confirmSellerPhone(phone.replace(/\D/g, ''), phoneCode),
    onSuccess: () => setStep('account'),
  })
  const requestAccount = useMutation({
    mutationFn: () => authApi.requestSellerAccount(),
    onSuccess: (result) => {
      setAccount(result)
      setAuthCode(result.devAuthCode)
    },
  })
  const confirmAccount = useMutation({
    mutationFn: () => authApi.confirmSellerAccount(account?.accountNo ?? '', authCode),
    onSuccess: () => setStep('profile'),
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

  const on = (setter: (v: string) => void) => (e: ChangeEvent<HTMLInputElement>) =>
    setter(e.target.value)
  const errorOf = (m: { isError: boolean; error: unknown }) =>
    m.isError ? toMessage(m.error) : undefined

  return (
    <>
      <header className="gnb">
        <div className="inner">
          <span className="brand">공구한</span>
          <span className="badge">판매자센터</span>
        </div>
      </header>

      <main className="wrap">
        <section className="section" style={{ paddingTop: 40 }}>
          <div className="sec-head">
            <div>
              <h2 className="sec-title">판매자 전환</h2>
              <p className="sec-sub">
                휴대폰·정산계좌 인증 후 판매자 정보를 등록하면 판매자센터를 이용할 수 있습니다.
              </p>
            </div>
          </div>

          <div className="split">
            <div>
              {/* 1단계: 휴대폰 인증 (목업) */}
              <Panel
                title="1. 휴대폰 인증"
                description={step === 'phone' ? '본인 명의 휴대폰으로 인증합니다' : '완료됨 ✓'}
              >
                {step === 'phone' ? (
                  <>
                    <TextField
                      label="휴대폰 번호"
                      inputMode="numeric"
                      placeholder="010-0000-0000"
                      value={phone}
                      onChange={on(setPhone)}
                      action={
                        <Button
                          variant="ghost"
                          onClick={() => requestPhone.mutate()}
                          disabled={requestPhone.isPending || phone.trim() === ''}
                        >
                          {requestPhone.isPending ? '요청 중…' : '인증요청'}
                        </Button>
                      }
                      error={errorOf(requestPhone)}
                      hint={phoneHint ? `${phoneHint.message} (코드: ${phoneHint.devCode})` : undefined}
                    />
                    {phoneHint && (
                      <TextField
                        label="인증코드"
                        value={phoneCode}
                        onChange={on(setPhoneCode)}
                        error={errorOf(confirmPhone)}
                        action={
                          <Button
                            onClick={() => confirmPhone.mutate()}
                            disabled={confirmPhone.isPending || phoneCode.trim() === ''}
                          >
                            {confirmPhone.isPending ? '확인 중…' : '확인'}
                          </Button>
                        }
                      />
                    )}
                  </>
                ) : (
                  <p className="small muted">휴대폰 인증을 완료했습니다.</p>
                )}
              </Panel>

              {/* 2단계: 정산계좌 1원 인증 (실제 SSAFY) */}
              {step !== 'phone' && (
                <Panel
                  className="mt16"
                  title="2. 정산계좌 1원 인증"
                  description={step === 'account' ? 'SSAFY 금융망으로 1원을 보내 확인합니다' : '완료됨 ✓'}
                >
                  {step === 'account' ? (
                    <>
                      {account === null ? (
                        <Button
                          block
                          onClick={() => requestAccount.mutate()}
                          disabled={requestAccount.isPending}
                        >
                          {requestAccount.isPending ? '계좌 확인·1원 송금 중…' : '정산계좌 만들고 1원 인증 받기'}
                        </Button>
                      ) : (
                        <>
                          <p className="small muted">
                            정산계좌 <b>{account.accountNo}</b> 로 1원을 보냈습니다. {account.message}
                          </p>
                          <TextField
                            label="1원 인증코드"
                            value={authCode}
                            onChange={on(setAuthCode)}
                            error={errorOf(confirmAccount)}
                            action={
                              <Button
                                onClick={() => confirmAccount.mutate()}
                                disabled={confirmAccount.isPending || authCode.trim() === ''}
                              >
                                {confirmAccount.isPending ? '확인 중…' : '확인'}
                              </Button>
                            }
                          />
                        </>
                      )}
                      {requestAccount.isError && (
                        <p className="ai-hint mt16" role="alert">
                          {toMessage(requestAccount.error)}
                        </p>
                      )}
                    </>
                  ) : (
                    <p className="small muted">정산계좌 1원 인증을 완료했습니다.</p>
                  )}
                </Panel>
              )}

              {/* 3단계: 판매자 정보 */}
              {step === 'profile' && (
                <Panel className="mt16" title="3. 판매자 정보" description="상호와 반품 수취 주소를 등록합니다">
                  <TextField label="상호명" required value={businessName} onChange={on(setBusinessName)} />
                  <FieldRow className="mt8">
                    <TextField
                      label="대표 연락처"
                      inputMode="numeric"
                      placeholder="010-0000-0000"
                      value={businessPhone}
                      onChange={on(setBusinessPhone)}
                    />
                    <TextField label="예금주" required value={accountHolder} onChange={on(setAccountHolder)} />
                  </FieldRow>
                  <TextField
                    label="반품 우편번호"
                    required
                    inputMode="numeric"
                    maxLength={5}
                    value={zipcode}
                    onChange={on(setZipcode)}
                  />
                  <TextField label="반품 기본주소" required value={address1} onChange={on(setAddress1)} />
                  <TextField label="반품 상세주소" value={address2} onChange={on(setAddress2)} />
                  {createProfile.isError && (
                    <p className="ai-hint mt16" role="alert">
                      {toMessage(createProfile.error)}
                    </p>
                  )}
                  <Button
                    block
                    className="mt16"
                    onClick={() => createProfile.mutate()}
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
              <Panel title="전환 후 이용할 수 있는 기능" description="판매자센터">
                <ul className="mt16">
                  <li>상품 관리 — 등록 · 수정 · 삭제</li>
                  <li>공동구매 — 생성 · 참여자 현황</li>
                  <li>배송 — 송장 등록 · 상태 변경</li>
                  <li>반품 — 승인 · 거부 · 수거 확인</li>
                  <li>정산 — 정산 내역 조회</li>
                </ul>
              </Panel>
              <Panel className="mt16" title="안내" description="전자상거래법 제10조">
                <p className="small muted" style={{ marginBottom: 0 }}>
                  정산계좌 1원 인증은 SSAFY 금융망으로 실제 1원을 보내 예금주를 확인합니다.
                  휴대폰 인증은 데모 환경이라 발송된 코드를 화면에 함께 표시합니다.
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
