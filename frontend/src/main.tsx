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

const container = document.getElementById('root')
if (!container) throw new Error('#root 요소를 찾을 수 없습니다.')

void enableMocking().then(() => {
  createRoot(container).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
