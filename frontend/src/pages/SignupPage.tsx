import { useState, type ChangeEvent } from 'react'

import {
  blocksSignup,
  isEmailRejection,
  isErrorOutcome,
  NEEDS_CHECK,
} from '@/features/auth/emailCheck'
import { useSignup } from '@/features/auth/useAuthMutations'
import { useEmailAvailability } from '@/features/auth/useEmailAvailability'
import { isApiError, toMessage } from '@/shared/api/errors'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { FieldRow, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'

interface FormState {
  email: string
  password: string
  nickname: string
  phone: string
  recipientName: string
  recipientPhone: string
  zipcode: string
  address1: string
  address2: string
}

const INITIAL: FormState = {
  email: '',
  password: '',
  nickname: '',
  phone: '',
  recipientName: '',
  recipientPhone: '',
  zipcode: '',
  address1: '',
  address2: '',
}

export default function SignupPage() {
  useDocumentTitle('회원가입')

  const [form, setForm] = useState<FormState>(INITIAL)
  const signup = useSignup()
  const emailCheck = useEmailAvailability()
  /** 중복확인을 건너뛰고 가입을 눌렀을 때만 켭니다. */
  const [emailCheckSkipped, setEmailCheckSkipped] = useState(false)

  const emailOutcome = emailCheck.outcomeOf(form.email)

  /** 입력 하나를 form 상태의 같은 이름 필드에 반영합니다. */
  const set =
    (key: keyof FormState) =>
    (event: ChangeEvent<HTMLInputElement>): void => {
      if (key === 'email') setEmailCheckSkipped(false)
      setForm((current) => ({ ...current, [key]: event.target.value }))
    }

  /** 서버가 돌려준 필드 검증 오류를 해당 입력에 붙입니다. */
  const fieldError = (field: string): string | undefined =>
    isApiError(signup.error) ? signup.error.fieldError(field) : undefined

  /**
   * 이메일 칸에 띄울 오류.
   *
   * 서버 검증 오류를 가장 먼저 보여주고, 가입이 이메일 때문에 막혔으면 하단 오류
   * 상자 대신 원인이 된 이메일 칸에 붙입니다. 그 다음이 중복확인 결과입니다.
   */
  const emailError =
    fieldError('email') ??
    (isEmailRejection(signup.error) ? toMessage(signup.error) : undefined) ??
    (isErrorOutcome(emailOutcome) ? emailOutcome.message : undefined) ??
    (emailCheckSkipped ? NEEDS_CHECK : undefined)

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    if (blocksSignup(emailOutcome, BACKEND_FEATURES.emailAvailability)) {
      setEmailCheckSkipped(true)
      return
    }
    signup.mutate({
      email: form.email.trim(),
      password: form.password,
      nickname: form.nickname,
      phone: form.phone.replace(/\D/g, ''),
      defaultAddress: {
        recipientName: form.recipientName,
        recipientPhone: form.recipientPhone.replace(/\D/g, ''),
        zipcode: form.zipcode,
        address1: form.address1,
        address2: form.address2,
      },
    })
  }

  return (
    <main className="wrap">
      <section className="narrow">
        <h2>회원가입</h2>
        <p>공동구매에 참여하려면 계정과 기본 배송지가 필요합니다.</p>

        <form onSubmit={submit} noValidate>
          <Panel title="계정 정보" description="로그인에 사용할 정보입니다">
            <TextField
              label="이메일"
              type="email"
              required
              autoComplete="email"
              placeholder="email@example.com"
              value={form.email}
              onChange={set('email')}
              error={emailError}
              hint={emailOutcome.status === 'idle' ? undefined : emailOutcome.message}
              action={
                <Button
                  variant="ghost"
                  onClick={() => {
                    setEmailCheckSkipped(false)
                    emailCheck.check(form.email)
                  }}
                  disabled={emailOutcome.status === 'checking'}
                >
                  {emailOutcome.status === 'checking' ? '확인 중…' : '중복확인'}
                </Button>
              }
            />
            <TextField
              label="비밀번호"
              type="password"
              required
              autoComplete="new-password"
              hint="8자 이상, 영문·숫자·특수문자를 섞어주세요."
              value={form.password}
              onChange={set('password')}
              error={fieldError('password')}
            />
            <FieldRow className="mt8">
              <TextField
                label="닉네임"
                required
                value={form.nickname}
                onChange={set('nickname')}
                error={fieldError('nickname')}
              />
              <TextField
                label="휴대폰 번호"
                required
                inputMode="numeric"
                placeholder="010-0000-0000"
                value={form.phone}
                onChange={set('phone')}
                error={fieldError('phone')}
              />
            </FieldRow>
          </Panel>

          <Panel className="mt16" title="기본 배송지" description="공동구매 상품 수령 주소">
            <FieldRow>
              <TextField
                label="받는 사람"
                required
                value={form.recipientName}
                onChange={set('recipientName')}
                error={fieldError('defaultAddress.recipientName')}
              />
              <TextField
                label="받는 분 연락처"
                required
                inputMode="numeric"
                placeholder="010-0000-0000"
                value={form.recipientPhone}
                onChange={set('recipientPhone')}
                error={fieldError('defaultAddress.recipientPhone')}
              />
            </FieldRow>
            <TextField
              label="우편번호"
              required
              inputMode="numeric"
              maxLength={5}
              value={form.zipcode}
              onChange={set('zipcode')}
              error={fieldError('defaultAddress.zipcode')}
            />
            <TextField
              label="기본 주소"
              required
              value={form.address1}
              onChange={set('address1')}
              error={fieldError('defaultAddress.address1')}
            />
            <TextField
              label="상세 주소"
              value={form.address2}
              onChange={set('address2')}
              error={fieldError('defaultAddress.address2')}
            />
          </Panel>

          {signup.isError && !isEmailRejection(signup.error) && (
            <p className="ai-hint mt16" role="alert">
              {toMessage(signup.error)}
            </p>
          )}

          <Button type="submit" block className="mt24" disabled={signup.isPending}>
            {signup.isPending ? '가입 중…' : '가입하기'}
          </Button>
        </form>
      </section>
    </main>
  )
}
