import { useEffect, useState } from 'react'

import { Link, useLocation } from 'react-router'

import { useSessionExpired, useSessionStore } from '@/features/auth/sessionStore'
import { useLogin } from '@/features/auth/useAuthMutations'
import { toMessage } from '@/shared/api/errors'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { TextField } from '@/shared/ui/Field'

/** 회원가입을 마치고 넘어왔다면 그 이메일을 미리 채워줍니다. */
function signedUpEmail(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'signedUpEmail' in state) {
    const { signedUpEmail: email } = state
    if (typeof email === 'string') return email
  }
  return ''
}

export default function LoginPage() {
  useDocumentTitle('로그인')

  const location = useLocation()
  const justSignedUp = signedUpEmail(location.state)

  const [email, setEmail] = useState(justSignedUp)
  const [password, setPassword] = useState('')

  /**
   * 세션이 만료돼 밀려왔다면 한 번만 알려줍니다.
   *
   * 화면에 그리는 도중 스토어를 건드리면 렌더가 다시 돌므로, 값을 지역에
   * 붙잡아 두고 화면을 떠날 때 지웁니다.
   */
  const expired = useSessionExpired()
  const [showExpired] = useState(expired)
  useEffect(() => useSessionStore.getState().clearExpired, [])

  const login = useLogin()

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    login.mutate({ email, password })
  }

  return (
    <main className="wrap">
      <section className="narrow">
        <h2>로그인</h2>
        <p>공동구매 참여와 거래 현황 확인을 위해 로그인해 주세요.</p>

        {showExpired && (
          <div className="guide" role="status">
            <div className="guide-head">로그인이 만료되었습니다</div>
            <p className="small" style={{ margin: 0 }}>
              일정 시간이 지나 자동으로 로그아웃되었습니다. 다시 로그인하면 보시던 화면으로
              돌아갑니다. 작성 중이던 내용은 저장되지 않습니다.
            </p>
          </div>
        )}

        {justSignedUp !== '' && (
          <div className="guide" role="status">
            <div className="guide-head">가입이 완료되었습니다</div>
            <p className="small" style={{ margin: 0 }}>
              방금 만든 계정으로 로그인해 주세요.
            </p>
          </div>
        )}

        <form onSubmit={submit} noValidate>
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
