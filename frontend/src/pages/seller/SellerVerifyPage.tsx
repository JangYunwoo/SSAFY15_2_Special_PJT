import { useState, type ChangeEvent } from 'react'
import { Link, Navigate } from 'react-router'
import { useMutation } from '@tanstack/react-query'

import { authApi } from '@/entities/auth/api'
import type { SellerAccountRequestResult } from '@/entities/auth/model'
import { useIsSeller, useSessionStore } from '@/features/auth/sessionStore'
import { SiteFooter } from '@/app/layouts/SiteFooter'
import { toMessage } from '@/shared/api/errors'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { useScrollTopOnChange } from '@/shared/lib/hooks/useScrollTopOnChange'
import { BrandMark } from '@/shared/ui/BrandMark'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { percent } from '@/shared/lib/format'
import { Button, ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { FieldRow, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { Timeline, type TimelineStep } from '@/shared/ui/Timeline'
import { PhoneField } from '@/shared/ui/PhoneField'
import { AddressFields } from '@/features/address/AddressFields'
import { SellerTerms } from '@/features/seller/SellerTerms'

type Step = 'account' | 'profile' | 'terms' | 'done'

/**
 * 진행 표시 (S15P21B205-231).
 *
 * 막대와 라벨만 둡니다. 지금 단계의 섹션 하나만 보여주므로 "3단계 중 n단계"
 * 글자까지 얹으면 같은 말을 두 번 하게 됩니다. 전환이 끝나면 "완료" 에 닿고 완료 화면을 보여줍니다.
 */
const STEP_ORDER: readonly Step[] = ['account', 'profile', 'terms', 'done']

function stepTimeline(step: Step): TimelineStep[] {
  const current = STEP_ORDER.indexOf(step)
  return ['정산계좌 인증', '판매자 정보', '이용 동의', '완료'].map((label, index) => ({
    label,
    state: index < current ? 'done' : index === current ? 'now' : 'todo',
  }))
}

/**
 * 오른쪽 안내. 왼쪽에서 지금 하는 일을 왜 하는지, 마치면 어떻게 되는지 단계마다 바꿔 적습니다.
 * 숫자는 정책 상수에서 가져와 정책이 바뀌어도 문구가 어긋나지 않게 합니다.
 */
function StepGuide({ step }: { step: Step }) {
  if (step === 'account') {
    return (
      <Panel title="안내" description="전자상거래법 제10조">
        <p className="small muted" style={{ marginBottom: 0 }}>
          정산계좌 1원 인증은 SSAFY 금융망으로 실제 1원을 보내 예금주를 확인합니다. 카드가 없으면
          인증 과정에서 정산계좌가 자동으로 개설됩니다.
        </p>
      </Panel>
    )
  }

  if (step === 'profile') {
    return (
      <Panel title="이 정보는 여기에 쓰여요">
        <dl className="verify-guide">
          <dt>상호명</dt>
          <dd>공동구매 상세의 판매자 카드에 그대로 표시됩니다.</dd>
          <dt>대표 연락처</dt>
          <dd>
            구매자 문의와 분쟁 처리 창구입니다. 판매자 신원 표시 의무(전자상거래법 제10조)에
            쓰입니다.
          </dd>
          <dt>예금주</dt>
          <dd>1단계에서 인증한 정산계좌의 예금주와 같아야 정산이 지급됩니다.</dd>
          <dt>반품지</dt>
          <dd>
            구매자가 반품하면 상품이 이 주소로 옵니다. 반품 요청은{' '}
            {POLICY_DEFAULTS.returnApprovalBusinessDays}영업일 안에 처리해야 합니다.
          </dd>
        </dl>
      </Panel>
    )
  }

  return (
    <Panel title={step === 'done' ? '이제 이렇게 진행돼요' : '전환하면 이렇게 진행돼요'}>
      <ol className="verify-flow">
        <li>상품 등록</li>
        <li>공동구매 열기</li>
        <li>모집 성사</li>
        <li>발송</li>
        <li>정산</li>
      </ol>
      <DataRow label="플랫폼 수수료">
        분담금 합계의 {percent(POLICY_DEFAULTS.platformFeeRate)}
      </DataRow>
      <DataRow label="발송 기한">
        성사 후 {POLICY_DEFAULTS.shippingDeadlineBusinessDays}영업일
      </DataRow>
      <DataRow label="정산 시점">배송완료 후 {POLICY_DEFAULTS.settlementEligibleDays}일</DataRow>
      <DataRow label="정산 대상">구매확정된 주문</DataRow>
      <p className="small muted mt16" style={{ marginBottom: 0 }}>
        모집 인원이 채워지지 않으면 참여자 전원에게 자동으로 전액 환불됩니다.
      </p>
    </Panel>
  )
}

/**
 * 판매자 전환 (SEL-03~05).
 *
 * 3단계로 진행합니다: 정산계좌 1원 인증(실제 SSAFY 금융망) → 판매자 정보 입력 →
 * 소비자 보호 안내 동의. 판매자 정보는 동의까지 마친 뒤 한 번에 보냅니다.
 * 마지막 단계가 성공하면 백엔드가 isSeller=true 인 새 토큰을 내려주며, 그 토큰으로
 * 세션을 갱신하고 완료 화면을 보여줍니다. 판매자센터로 바로 넘기면 전환이 됐는지,
 * 다음에 무엇을 하면 되는지 말해 줄 곳이 없습니다.
 *
 * 휴대폰 인증은 SSAFY에 API가 없고 은행 업무도 아니라 제거했습니다. 판매자에게 실제로
 * 필요한 것은 정산 받을 계좌가 본인 것인지 확인하는 1원 인증입니다. 데모 편의로 1원
 * 인증코드는 서버가 응답에 함께 내려주므로 자동으로 채워 넣습니다.
 */
export default function SellerVerifyPage() {
  useDocumentTitle('판매자 전환')

  const isSeller = useIsSeller()
  const signIn = useSessionStore((state) => state.signIn)

  const [step, setStep] = useState<Step>('account')
  // 단계는 주소가 아니라 화면 안의 상태라, 넘어갈 때 직접 맨 위로 올립니다.
  useScrollTopOnChange(step)

  const [account, setAccount] = useState<SellerAccountRequestResult | null>(null)
  const [authCode, setAuthCode] = useState('')

  const [businessName, setBusinessName] = useState('')
  const [businessPhone, setBusinessPhone] = useState('')
  const [accountHolder, setAccountHolder] = useState('')
  const [zipcode, setZipcode] = useState('')
  const [address1, setAddress1] = useState('')
  const [address2, setAddress2] = useState('')
  const [allAgreed, setAllAgreed] = useState(false)

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
    onSuccess: (tokens) => {
      // 단계를 먼저 바꿉니다. 토큰이 들어가면 isSeller 가 참이 되어 아래 리다이렉트에 걸립니다.
      setStep('done')
      signIn(tokens)
    },
  })

  // 이미 전환된 계정이 주소로 들어오면 판매자센터로 보냅니다. 방금 전환을 마친 완료 화면은 예외입니다.
  if (isSeller && step !== 'done') return <Navigate to="/seller" replace />

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
                정산계좌 1원 인증, 판매자 정보 입력, 이용 동의를 마치면 판매자센터를 이용할 수
                있습니다.
              </p>
            </div>
          </div>

          <div className="verify-steps">
            <Timeline steps={stepTimeline(step)} />
          </div>

          <div className="split">
            <div>
              {/*
                지금 단계의 패널 하나만 그립니다. 지난 단계를 "완료됨" 으로 접어 쌓아 두면
                화면이 길어지기만 하고, 어디까지 왔는지는 위 진행 막대가 이미 보여줍니다.
              */}
              {/* 1단계: 정산계좌 1원 인증 (실제 SSAFY) */}
              {step === 'account' && (
                <Panel
                  title="1. 정산계좌 1원 인증"
                  description="SSAFY 금융망으로 1원을 보내 예금주를 확인합니다"
                >
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
                </Panel>
              )}

              {/* 2단계: 판매자 정보 */}
              {step === 'profile' && (
                <Panel title="2. 판매자 정보" description="상호와 반품 수취 주소를 등록합니다">
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
                  <Button
                    block
                    className="mt16"
                    onClick={() => {
                      setStep('terms')
                    }}
                    disabled={
                      businessName.trim() === '' ||
                      accountHolder.trim() === '' ||
                      zipcode.trim() === '' ||
                      address1.trim() === ''
                    }
                  >
                    다음
                  </Button>
                </Panel>
              )}

              {/* 3단계: 이용 동의. 세 항목을 모두 읽고 체크해야 전환 버튼이 켜집니다 */}
              {step === 'terms' && (
                <Panel
                  title="3. 이용 동의"
                  description="판매자가 지켜야 할 소비자 보호 의무를 끝까지 읽고 동의합니다"
                >
                  <SellerTerms onAllAgreedChange={setAllAgreed} />
                  {createProfile.isError && (
                    <p className="ai-hint mt16" role="alert">
                      {toMessage(createProfile.error)}
                    </p>
                  )}
                  {/*
                    두 버튼을 처음부터 둡니다. 전환 버튼이 체크 뒤에야 튀어나오면 버튼 순서가
                    바뀌어 보입니다. 세 항목에 모두 동의해야 파랗게 켜집니다.
                    이전 단계 버튼은 지난 단계 요약을 없앤 뒤 판매자 정보를 고칠 유일한 길입니다.
                  */}
                  <Button
                    variant="ghost"
                    block
                    className="mt16"
                    onClick={() => {
                      setStep('profile')
                    }}
                  >
                    이전 단계로
                  </Button>
                  <Button
                    block
                    className="mt8 verify-submit"
                    onClick={() => {
                      createProfile.mutate()
                    }}
                    disabled={!allAgreed || createProfile.isPending}
                  >
                    {createProfile.isPending ? '전환 중…' : '판매자로 전환하기'}
                  </Button>
                </Panel>
              )}

              {/* 완료: 전환 결과와 다음 할 일 */}
              {step === 'done' && (
                <Panel
                  title="판매자 전환을 마쳤습니다"
                  description="이제 판매자센터를 이용할 수 있습니다"
                >
                  <p className="para">
                    {businessName} 이름으로 판매자 등록이 끝났습니다. 상품을 등록하고 공동구매를
                    열면 모집이 시작됩니다.
                  </p>
                  <ButtonLink block className="verify-submit" to="/seller/products/new" replace>
                    첫 상품 등록하기
                  </ButtonLink>
                  <ButtonLink variant="ghost" block className="mt8" to="/seller" replace>
                    판매자센터로 가기
                  </ButtonLink>
                </Panel>
              )}
            </div>

            <aside>
              <StepGuide step={step} />
            </aside>
          </div>
        </section>
      </main>

      <SiteFooter compact />
    </>
  )
}
