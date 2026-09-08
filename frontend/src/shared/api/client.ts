import { env } from '@/shared/config/env'

import { ApiError, NetworkError } from './errors'
import type { ApiResponse, NoContent, PageMeta, Paged } from './types'

type Query = Record<string, string | number | boolean | null | undefined>

interface RequestOptions {
  /** 결제 준비·승인, 환불 재처리, 정산 실행에 필요 (명세서 1.2) */
  idempotencyKey?: string
  query?: Query
  signal?: AbortSignal
}

interface BodyOptions extends RequestOptions {
  /** FormData 를 넘기면 Content-Type 을 브라우저가 정하도록 비웁니다. */
  body?: unknown
}

/**
 * 액세스 토큰 공급자. 순환 참조를 피하려고 auth 스토어가 부팅 시 주입합니다.
 * (스토어 → client → 스토어 형태의 import 고리를 만들지 않기 위함)
 */
let accessTokenProvider: () => string | null = () => null
/** 브릿지가 연결되기 전에는 아무것도 하지 않습니다. */
let onUnauthorized: () => void = noop

function noop(): void {
  // 의도적으로 비어 있습니다.
}

export function configureAuthBridge(options: {
  getAccessToken: () => string | null
  onUnauthorized: () => void
}): void {
  accessTokenProvider = options.getAccessToken
  onUnauthorized = options.onUnauthorized
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

async function request<TData, TMeta = null>(
  method: string,
  path: string,
  { body, idempotencyKey, query, signal }: BodyOptions = {},
): Promise<ApiResponse<TData, TMeta>> {
  const headers = new Headers({ Accept: 'application/json' })

  const token = accessTokenProvider()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey)

  const isFormData = body instanceof FormData
  if (body !== undefined && !isFormData) headers.set('Content-Type', 'application/json')

  let response: Response
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      credentials: 'include',
      ...(body === undefined ? {} : { body: isFormData ? body : JSON.stringify(body) }),
      ...(signal ? { signal } : {}),
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause
    throw new NetworkError(cause)
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

    // 토큰이 죽었으면 세션을 정리해 로그인 화면으로 보냅니다.
    if (error.isUnauthorized) onUnauthorized()
    throw error
  }

  return payload
}

/** 성공 응답의 `data` 만 꺼내는 기본 형태 */
async function unwrap<TData>(method: string, path: string, options?: BodyOptions): Promise<TData> {
  const payload = await request<TData>(method, path, options)
  // request() 가 실패 응답을 이미 throw 했으므로 여기서는 성공만 남습니다.
  return (payload as { data: TData }).data
}

/** 목록 응답의 `data` + `meta` 를 함께 꺼냅니다. */
async function unwrapPage<TItem>(path: string, options?: RequestOptions): Promise<Paged<TItem>> {
  const payload = await request<TItem[], PageMeta>('GET', path, options)
  const { data, meta } = payload as { data: TItem[]; meta: PageMeta }
  return { items: data, meta }
}

export const api = {
  get: <TData>(path: string, options?: RequestOptions) => unwrap<TData>('GET', path, options),
  post: <TData>(path: string, body?: unknown, options?: RequestOptions) =>
    unwrap<TData>('POST', path, { ...options, body }),
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
