import { useState } from 'react'

import { useNavigate } from 'react-router'

import { useSessionStore } from '@/features/auth/sessionStore'
import { AgreementList } from '@/features/seller/AgreementList'
import { useAgreements } from '@/features/seller/useAgreements'
import { VerificationCodeStep } from '@/features/seller/VerificationCodeStep'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { FieldRow, SelectField, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { RadioGroup } from '@/shared/ui/RadioGroup'
import { SiteFooter } from '@/app/layouts/SiteFooter'

const METHODS = [
  { value: 'PASS', label: 'PASS' },
  { value: 'KAKAO', label: '카카오' },
  { value: 'CERT', label: '공동인증서' },
] as const

const TELCOS = ['SKT', 'KT', 'LG U+', '알뜰폰'].map((name) => ({ value: name, label: name }))

const TERMS = [
  '본인확인 서비스 이용약관',
  '개인정보 수집·이용 동의',
  '고유식별정보 처리 동의',
] as const

type Method = (typeof METHODS)[number]['value']

/**
 * 판매자 본인인증 (전자상거래법 제10조).
 *
 * 본인확인기관 연동은 백엔드 API가 정해지지 않아 아직 붙이지 않았습니다.
 * 인증 성공 시 세션의 sellerVerified 를 세워 판매자센터 진입을 허용합니다.
 */
export default function SellerVerifyPage() {
  useDocumentTitle('판매자 본인인증')

  const navigate = useNavigate()
  const setSellerVerified = useSessionStore((state) => state.setSellerVerified)

  const [method, setMethod] = useState<Method>('PASS')
  const [name, setName] = useState('')
  const [birth, setBirth] = useState('')
  const [telco, setTelco] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [requested, setRequested] = useState(false)

  const agreements = useAgreements(TERMS.length)

  const isFormReady =
    name.trim() !== '' &&
    birth.length === 8 &&
    (method === 'CERT' || (telco !== '' && phoneNumber.replace(/\D/g, '').length >= 10)) &&
    agreements.allChecked

  const request = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    setRequested(true)
  }

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
              <h2 className="sec-title">판매자 본인인증</h2>
              <p className="sec-sub">판매자센터는 본인인증을 마친 뒤 이용할 수 있습니다.</p>
            </div>
          </div>

          <div className="split">
            <div>
              <form className="panel" onSubmit={request}>
                <h3>인증 수단</h3>
                <p className="sub">휴대폰 본인확인 서비스로 인증합니다</p>

                <RadioGroup
                  legend="인증 수단"
                  options={METHODS}
                  value={method}
                  onChange={setMethod}
                />

                <FieldRow className="mt24">
                  <TextField
                    label="이름"
                    required
                    autoComplete="name"
                    placeholder="실명을 입력하세요"
                    value={name}
                    onChange={(event) => {
                      setName(event.target.value)
                    }}
                  />
                  <TextField
                    label="생년월일"
                    required
                    inputMode="numeric"
                    maxLength={8}
                    placeholder="8자리 (예: 19980304)"
                    value={birth}
                    onChange={(event) => {
                      setBirth(event.target.value.replace(/\D/g, ''))
                    }}
                  />
                </FieldRow>

                {method !== 'CERT' && (
                  <FieldRow className="mt8">
                    <SelectField
                      label="통신사"
                      required
                      placeholder="선택"
                      value={telco}
                      onChange={(event) => {
                        setTelco(event.target.value)
                      }}
                      options={TELCOS}
                    />
                    <TextField
                      label="휴대폰 번호"
                      required
                      inputMode="numeric"
                      maxLength={13}
                      placeholder="010-0000-0000"
                      autoComplete="tel"
                      value={phoneNumber}
                      onChange={(event) => {
                        setPhoneNumber(event.target.value)
                      }}
                    />
                  </FieldRow>
                )}

                <AgreementList className="mt24" terms={TERMS} state={agreements} />

                <Button type="submit" block className="mt24" disabled={!isFormReady || requested}>
                  인증 요청하기
                </Button>
                {!agreements.allChecked && (
                  <p className="ai-hint">필수 약관에 모두 동의해야 인증을 요청할 수 있습니다.</p>
                )}
              </form>

              {requested && (
                <VerificationCodeStep
                  className="mt16"
                  phoneNumber={phoneNumber}
                  onVerified={() => {
                    setSellerVerified(true)
                    void navigate('/seller', { replace: true })
                  }}
                  onResend={() => {
                    setRequested(true)
                  }}
                />
              )}
            </div>

            <aside>
              <Panel title="본인인증이 필요한 이유" description="전자상거래법 제10조">
                <div className="guide mt16">
                  <div className="guide-head">확인하는 항목</div>
                  <ul>
                    <li>판매자의 실명과 연락처를 확인합니다.</li>
                    <li>확인된 명의로만 정산 계좌를 등록할 수 있습니다.</li>
                    <li>인증은 판매자센터 최초 진입 시 한 번만 진행합니다.</li>
                    <li>입력한 정보는 본인확인기관에만 전달되며 공구한은 저장하지 않습니다.</li>
                  </ul>
                </div>
                <p className="small muted mt16" style={{ marginBottom: 0 }}>
                  만 19세 미만은 판매자로 등록할 수 없습니다. 법인 사업자는 인증 후 사업자등록번호를
                  추가로 등록해야 합니다.
                </p>
              </Panel>

              <Panel className="mt16" title="인증이 안 되나요" description="이럴 때 확인하세요">
                <DataRow label="명의가 다른 휴대폰">공동인증서</DataRow>
                <DataRow label="해외 체류 중">공동인증서</DataRow>
                <DataRow label="알뜰폰 사용">통신사 선택 확인</DataRow>
              </Panel>
            </aside>
          </div>
        </section>
      </main>

      <SiteFooter compact />
    </>
  )
}
