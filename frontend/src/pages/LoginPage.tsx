import { useState } from 'react'

import { Link } from 'react-router'

import { useLogin } from '@/features/auth/useAuthMutations'
import { toMessage } from '@/shared/api/errors'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { RadioGroup } from '@/shared/ui/RadioGroup'
import { TextField } from '@/shared/ui/Field'

type Audience = 'buyer' | 'seller'

const AUDIENCE_OPTIONS = [
  { value: 'buyer', label: '구매자' },
  { value: 'seller', label: '판매자' },
] as const

export default function LoginPage() {
  useDocumentTitle('로그인')

  const [audience, setAudience] = useState<Audience>('buyer')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const login = useLogin(audience)

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    login.mutate({ email, password })
  }

  return (
    <main className="wrap">
      <section className="narrow">
        <h2>로그인</h2>
        <p>공동구매 참여와 거래 현황 확인을 위해 로그인해 주세요.</p>

        <form onSubmit={submit} noValidate>
          <RadioGroup
            legend="로그인 유형"
            options={AUDIENCE_OPTIONS}
            value={audience}
            onChange={setAudience}
          />

          <TextField
            label="이메일"
            type="email"
            required
            autoComplete="email"
            placeholder="email@example.com"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value)
            }}
          />

          <TextField
            label="비밀번호"
            type="password"
            required
            autoComplete="current-password"
            placeholder="비밀번호"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value)
            }}
          />

          {login.isError && (
            <p className="ai-hint mt16" role="alert">
              {toMessage(login.error)}
            </p>
          )}

          <Button type="submit" block className="mt24" disabled={login.isPending}>
            {login.isPending ? '로그인 중…' : '로그인'}
          </Button>
        </form>

        <Link className="btn-ghost mt8" style={{ display: 'block' }} to="/signup">
          회원가입
        </Link>
      </section>
    </main>
  )
}
