import { env } from '@/shared/config/env'

import { checkContract } from './contract'
import { ApiError, NetworkError } from './errors'
import type { SchemaName } from './schemas'
import type { ApiResponse, NoContent, PageMeta, Paged } from './types'

type Query = Record<string, string | number | boolean | null | undefined>

interface RequestOptions {
  /** 결제 준비·승인, 환불 재처리, 정산 실행에 필요 (명세서 1.2) */
  idempotencyKey?: string
  query?: Query
  signal?: AbortSignal
  /**
   * 응답 검증 스키마 이름(shared/api/schemas.ts 의 레지스트리 키).
   * 넘기면 개발 중에만 서버 응답 모양을 확인합니다. 목록 응답에는 배열이
   * 아니라 "항목 하나"의 스키마 이름을 넘깁니다.
   *
   * 객체가 아니라 이름인 이유는 contract.ts 주석에 적어 두었습니다.
   */
  schema?: SchemaName
  /**
   * 401 을 받아도 토큰 재발급을 시도하지 않습니다.
   * 재발급 요청 자신이 401 일 때 무한히 되도는 것을 막는 용도입니다.
   */
  skipRefresh?: boolean
}

interface BodyOptions extends RequestOptions {
  /** FormData 를 넘기면 Content-Type 을 브라우저가 정하도록 비웁니다. */
  body?: unknown
  /** pagehide 중에도 세션 종료 로그 전송을 이어갑니다. */
  keepalive?: boolean
}

/**
 * 액세스 토큰 공급자. 순환 참조를 피하려고 auth 스토어가 부팅 시 주입합니다.
 * (스토어 → client → 스토어 형태의 import 고리를 만들지 않기 위함)
 */
let accessTokenProvider: () => string | null = () => null
/** 재발급 성공 여부를 돌려줍니다. 브릿지가 연결되기 전에는 항상 실패로 봅니다. */
let refreshTokens: () => Promise<boolean> = () => Promise.resolve(false)
/** 브릿지가 연결되기 전에는 아무것도 하지 않습니다. */
let onUnauthorized: () => void = noop

function noop(): void {
  // 의도적으로 비어 있습니다.
}

export function configureAuthBridge(options: {
  getAccessToken: () => string | null
  refresh: () => Promise<boolean>
  onUnauthorized: () => void
}): void {
  accessTokenProvider = options.getAccessToken
  refreshTokens = options.refresh
  onUnauthorized = options.onUnauthorized
}

/** 진행 중인 재발급. 여러 요청이 동시에 401 을 받아도 재발급은 한 번만 합니다. */
let refreshing: Promise<boolean> | null = null

function ensureRefreshed(): Promise<boolean> {
  refreshing ??= refreshTokens().finally(() => {
    refreshing = null
  })
  return refreshing
}

function buildUrl(path: string, query?: Query): string {
  const url = `${env.apiBaseUrl}${path}`
  if (!query) return url

  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined || value === '') continue
    search.set(key, String(value))
  }
  const qs = search.toString()
  return qs ? `${url}?${qs}` : url
}

async function parse<TData, TMeta>(response: Response): Promise<ApiResponse<TData, TMeta>> {
  // 204 No Content 는 본문이 없습니다.
  if (response.status === 204) {
    return { success: true, data: undefined as TData, meta: null as TMeta, traceId: '' }
  }
  try {
    return (await response.json()) as ApiResponse<TData, TMeta>
  } catch (cause) {
    throw new NetworkError(cause)
  }
}

/** 한 번 보냅니다. 토큰은 보낼 때마다 새로 읽어 재발급된 값이 반영됩니다. */
async function send(method: string, path: string, options: BodyOptions): Promise<Response> {
  const { body, idempotencyKey, query, signal, keepalive } = options
  const headers = new Headers({ Accept: 'application/json' })

  const token = accessTokenProvider()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey)

  const isFormData = body instanceof FormData
  if (body !== undefined && !isFormData) headers.set('Content-Type', 'application/json')

  try {
    return await fetch(buildUrl(path, query), {
      method,
      headers,
      credentials: 'include',
      ...(keepalive === undefined ? {} : { keepalive }),
      ...(body === undefined ? {} : { body: isFormData ? body : JSON.stringify(body) }),
      ...(signal ? { signal } : {}),
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause
    throw new NetworkError(cause)
  }
}

async function request<TData, TMeta = null>(
  method: string,
  path: string,
  options: BodyOptions = {},
): Promise<ApiResponse<TData, TMeta>> {
  let response = await send(method, path, options)

  /*
   * 액세스 토큰이 만료되면 조용히 재발급하고 같은 요청을 한 번만 다시 보냅니다.
   * 재발급까지 실패하면 아래에서 세션을 정리합니다.
   */
  if (response.status === 401 && options.skipRefresh !== true) {
    const renewed = await ensureRefreshed()
    if (renewed) response = await send(method, path, options)
  }

  const payload = await parse<TData, TMeta>(response)

  if (!response.ok || !payload.success) {
    const error = payload.success
      ? new ApiError(response.status, {
          code: 'UNKNOWN',
          message: '요청을 처리할 수 없습니다.',
          fieldErrors: [],
        })
      : new ApiError(response.status, payload.error, payload.traceId)

    // 재발급으로도 살리지 못한 401 이면 세션을 정리해 로그인 화면으로 보냅니다.
    if (error.isUnauthorized) onUnauthorized()
    throw error
  }

  return payload
}

/** 성공 응답의 `data` 만 꺼내는 기본 형태 */
async function unwrap<TData>(method: string, path: string, options?: BodyOptions): Promise<TData> {
  const payload = await request<TData>(method, path, options)
  // request() 가 실패 응답을 이미 throw 했으므로 여기서는 성공만 남습니다.
  const data = (payload as { data: TData }).data
  checkContract(options?.schema, data, `${method} ${path}`)
  return data
}

/** 목록 응답의 `data` + `meta` 를 함께 꺼냅니다. */
async function unwrapPage<TItem>(path: string, options?: RequestOptions): Promise<Paged<TItem>> {
  const payload = await request<TItem[], PageMeta>('GET', path, options)
  const { data, meta } = payload as { data: TItem[]; meta: PageMeta }
  // 목록은 첫 항목만 봐도 모양이 드러납니다. 빈 목록은 검증할 항목 자체가 없습니다.
  if (data.length > 0) checkContract(options?.schema, data[0], `GET ${path} [0]`)
  return { items: data, meta }
}

export const api = {
  get: <TData>(path: string, options?: RequestOptions) => unwrap<TData>('GET', path, options),
  post: <TData>(path: string, body?: unknown, options?: RequestOptions) =>
    unwrap<TData>('POST', path, { ...options, body }),
  postKeepalive: <TData>(path: string, body?: unknown, options?: RequestOptions) =>
    unwrap<TData>('POST', path, { ...options, body, keepalive: true }),
  patch: <TData>(path: string, body?: unknown, options?: RequestOptions) =>
    unwrap<TData>('PATCH', path, { ...options, body }),
  put: <TData>(path: string, body?: unknown, options?: RequestOptions) =>
    unwrap<TData>('PUT', path, { ...options, body }),
  delete: <TData = NoContent>(path: string, options?: RequestOptions) =>
    unwrap<TData>('DELETE', path, options),
  getPage: unwrapPage,
} as const

/** 명세서 1.2 의 Idempotency-Key 용 UUID */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}
