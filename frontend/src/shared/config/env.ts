/**
 * import.meta.env 를 한곳에서만 읽습니다.
 *
 * 개발 중에는 Vite 프록시(`/api` → VITE_API_PROXY_TARGET)를 거쳐 실제 백엔드로
 * 나갑니다. 백엔드의 context-path 가 `/api/v1` 이라 기본값을 그대로 씁니다.
 */
export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  isDev: import.meta.env.DEV,
} as const
