/// <reference types="vite/client" />

/** .env 로 주입되는 값. 여기 없는 키를 읽으면 타입 오류가 납니다. */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_API_PROXY_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
