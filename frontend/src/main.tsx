import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { App } from '@/app/App'
import { env } from '@/shared/config/env'

import './styles/tokens.css'
import './styles/global.css'

/**
 * 백엔드가 준비되기 전까지는 MSW 로 명세서 응답을 흉내냅니다.
 *
 * import.meta.env.DEV 를 먼저 확인하는 이유: 이 값은 빌드 시점에 치환되므로
 * 프로덕션 번들에서는 아래 dynamic import 가 죽은 코드로 제거됩니다.
 * (런타임 플래그만 보면 쓰이지 않는 목 서버 청크가 dist 에 남습니다)
 */
async function enableMocking(): Promise<void> {
  if (!import.meta.env.DEV || !env.useMocks) return
  const { worker } = await import('./mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass' })
}

/**
 * 목 서버 시작이 실패한 이유를 화면에 띄웁니다.
 *
 * 서비스워커는 dev 서버를 껐다 켜도 브라우저에 남아 있어서, 재시작 뒤에
 * 등록이 깨지는 일이 종종 있습니다. 콘솔을 열지 않아도 원인과 조치를
 * 알 수 있어야 합니다.
 *
 * React 를 쓰지 않고 DOM 을 직접 만드는 이유: 이 함수는 아래 DEV 분기에서만
 * 불리므로 프로덕션 번들에서 함께 제거됩니다. 스타일도 전역 CSS 를 늘리지
 * 않도록 인라인으로 둡니다.
 */
function showMockFailureNotice(cause: unknown): void {
  const box = document.createElement('div')
  box.setAttribute('role', 'alert')
  box.style.cssText = [
    'position:fixed',
    'inset:0 0 auto 0',
    'z-index:9999',
    'padding:16px 20px',
    'background:#fff1f1',
    'border-bottom:1px solid #ffd7d9',
    'color:#a2191f',
    'font:13px/1.7 "Noto Sans KR",sans-serif',
  ].join(';')

  const title = document.createElement('b')
  title.textContent = '목 서버(MSW)를 시작하지 못해 데이터를 불러올 수 없습니다.'
  title.style.cssText = 'display:block;margin-bottom:6px'

  const steps = document.createElement('div')
  steps.textContent =
    '1) F12 → Application → Service Workers 에서 mockServiceWorker.js 를 Unregister ' +
    '2) Ctrl+Shift+R 로 강력 새로고침 3) 그래도 안 되면 dev 서버를 재시작하세요.'

  const reason = document.createElement('div')
  reason.textContent = `원인: ${cause instanceof Error ? cause.message : String(cause)}`
  reason.style.cssText = 'margin-top:6px;color:#5f6368;font-size:12px'

  const close = document.createElement('button')
  close.type = 'button'
  close.textContent = '닫기'
  close.style.cssText =
    'position:absolute;top:12px;right:16px;background:none;border:0;' +
    'color:#5f6368;font-size:12px;cursor:pointer'
  close.addEventListener('click', () => {
    box.remove()
  })

  box.append(title, steps, reason, close)
  document.body.prepend(box)
}

const container = document.getElementById('root')
if (!container) throw new Error('#root 요소를 찾을 수 없습니다.')

/**
 * 목 서버가 실패해도 화면은 떠야 합니다.
 *
 * 전에는 enableMocking() 이 reject 되면 then 이 실행되지 않아 아무것도
 * 렌더링되지 않았고, 화면에 오류 문구조차 없어 원인을 알 수 없었습니다.
 */
void enableMocking()
  .catch((cause: unknown) => {
    console.error('[mock] 시작 실패', cause)
    if (import.meta.env.DEV) showMockFailureNotice(cause)
  })
  .finally(() => {
    createRoot(container).render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
  })
