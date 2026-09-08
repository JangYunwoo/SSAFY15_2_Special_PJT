/** import.meta.env 를 한곳에서만 읽습니다. */
export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  useMocks: import.meta.env.VITE_API_MOCKING === 'true',
  isDev: import.meta.env.DEV,
} as const
