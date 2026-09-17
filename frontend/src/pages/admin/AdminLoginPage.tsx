import { useState } from 'react'

import { Link } from 'react-router'

import { useAdminLogin } from '@/features/admin/useAdminAuth'
import { toMessage } from '@/shared/api/errors'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { BrandMark } from '@/shared/ui/BrandMark'
import { Button } from '@/shared/ui/Button'
import { TextField } from '@/shared/ui/Field'
import { SiteFooter } from '@/app/layouts/SiteFooter'

/**
 * AUTH-05 관리자 로그인.
 *
 * 구매자 로그인(`/login`)과 화면을 나누었습니다. 계정 테이블도 엔드포인트도
 * 달라서 한 폼에서 둘 다 받으면 "어느 쪽 비밀번호가 틀렸는지" 를 알려줄 수 없고,
 * 구매자 계정으로 관리자 로그인을 시도하면 원인이 보이지 않는 401 만 남습니다.
 *
 * 헤더에 탭이 없습니다. 로그인하기 전에는 들어갈 수 있는 메뉴가 없어서,
 * 탭을 보여주면 전부 로그인 화면으로 되튕깁니다.
 */
export default function AdminLoginPage() {
  useDocumentTitle('관리자 로그인')

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const login = useAdminLogin()

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    login.mutate({ email, password })
  }

  return (
    <>
      <header className="gnb">
        <div className="inner">
          <Link className="brand" to="/">
            <BrandMark />
          </Link>
          <span className="badge">관리자 콘솔</span>
        </div>
      </header>

      <main className="wrap">
        <section className="narrow">
          <h2>관리자 로그인</h2>
          <p>운영 관리자 계정으로만 접근할 수 있습니다.</p>

          <form onSubmit={submit} noValidate>
            <TextField
              label="관리자 이메일"
              type="email"
              required
              autoComplete="email"
              placeholder="admin@gongguhan.dev"
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

          <div className="guide mt24">
            <div className="guide-head">안내</div>
            <ul>
              <li>관리자 계정은 가입 경로가 없습니다. 운영팀에 요청해 발급받습니다.</li>
              <li>
                로그인하면 같은 브라우저의 구매자·판매자 세션은 로그아웃됩니다. 토큰을 하나만
                보관하기 때문입니다.
              </li>
              <li>관리자 조치는 모두 감사 로그에 기록됩니다.</li>
            </ul>
          </div>

          <Link className="btn-ghost mt16" style={{ display: 'block' }} to="/">
            서비스 화면으로
          </Link>
        </section>
      </main>

      <SiteFooter compact />
    </>
  )
}
