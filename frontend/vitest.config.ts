import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'

/**
 * 테스트 설정을 vite.config.ts 와 분리한 이유:
 * 빌드 설정에 test 필드를 얹으면 프로덕션 빌드가 vitest 타입에 묶입니다.
 *
 * 대상은 브라우저 API 를 쓰지 않는 순수 함수(금액 계산·상태 매핑·표기)입니다.
 * 그래서 jsdom 없이 node 환경에서 돌립니다.
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
