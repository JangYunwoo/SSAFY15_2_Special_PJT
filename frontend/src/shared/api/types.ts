/**
 * API 명세서 1.3 / 1.4 공통 응답 계약.
 * 모든 엔드포인트가 이 봉투(envelope)로 감싸서 응답합니다.
 */

export interface PageMeta {
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface FieldError {
  field: string
  message: string
}

export interface ApiErrorBody {
  code: string
  message: string
  fieldErrors: FieldError[]
}

export interface ApiSuccess<TData, TMeta = null> {
  success: true
  data: TData
  meta: TMeta
  traceId: string
}

export interface ApiFailure {
  success: false
  error: ApiErrorBody
  traceId: string
}

export type ApiResponse<TData, TMeta = null> = ApiSuccess<TData, TMeta> | ApiFailure

/** 목록 응답. `meta` 에 페이지 정보가 실립니다. */
export type ApiPage<TItem> = ApiSuccess<TItem[], PageMeta>

/** 훅에서 다루기 쉽게 풀어낸 페이지 결과 */
export interface Paged<TItem> {
  items: TItem[]
  meta: PageMeta
}

export interface PageParams {
  page?: number
  /** 최대 100 (명세서 1.5) */
  size?: number
}

/**
 * 명세서 1.5: 식별자는 JSON Number 범위를 넘길 수 있어 문자열로 주고받습니다.
 * KRW 금액은 소수점 없는 정수, 디지털 자산 수량·환율은 문자열 Decimal 입니다.
 */
export type Id = string
export type Krw = number
export type Decimal = string
/** ISO-8601 UTC. 예: `2026-09-03T01:30:00Z` */
export type IsoDateTime = string

export type Role = 'BUYER' | 'SELLER' | 'ADMIN'

/** 204 No Content 처럼 본문이 없는 응답의 data 타입 */
export type NoContent = undefined
