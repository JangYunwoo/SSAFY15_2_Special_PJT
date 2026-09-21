import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'

/**
 * 테스트 설정을 vite.config.ts 와 분리한 이유:
 * 빌드 설정에 test 필드를 얹으면 프로덕션 빌드가 vitest 타입에 묶입니다.
 *
 * 순수 함수와 서버 렌더링으로 확인할 수 있는 컴포넌트 계약을 검사합니다.
 * 실제 DOM 이벤트는 브라우저에서 검증하므로 jsdom 없이 node 환경에서 돌립니다.
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
