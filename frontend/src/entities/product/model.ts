import type { Id, IsoDateTime, Krw, PageParams } from '@/shared/api/types'
import type { RestrictionReviewStatus } from '@/entities/model'

export interface Product {
  id: Id
  name: string
  brand: string | null
  modelName: string | null
  color: string | null
  categoryId: Id
  categoryName: string
  /** 공동구매 1회분 총액 */
  price: Krw
  description: string
  images: string[]
  /** AI 추출 결과를 검수해 등록했는지 (명세서 3장) */
  aiExtracted: boolean
  withdrawalRestrictionReasonId: Id | null
  withdrawalRestrictionReason: string | null
  restrictionReviewStatus: RestrictionReviewStatus
  restrictionEvidenceUrl: string | null
  /** 연결된 공동구매. 있으면 상품을 수정할 수 없습니다. */
  linkedGroupBuyId: Id | null
  linkedGroupBuyStatus: string | null
  createdAt: IsoDateTime
}

export interface ProductListParams extends PageParams {
  keyword?: string
}

/**
 * PRD-01 등록 · PRD-04 수정 요청의 JSON 파트 (백엔드 ProductUpsertRequest).
 * 이미지는 이 객체가 아니라 multipart 의 images 파트로 따로 보냅니다.
 */
export interface ProductInput {
  name: string
  /**
   * 선택 항목 셋. 백엔드는 @Size 만 걸고 컬럼도 nullable 이라 비워서 보낼 수
   * 있습니다. 빈 문자열로 보내면 "값이 없음" 과 "빈 값" 이 구분되지 않아
   * (IS NULL 조회에 걸리지 않고 화면에도 빈 줄이 남습니다) null 로 보냅니다.
   */
  brand: string | null
  modelName: string | null
  color: string | null
  categoryId: Id
  price: Krw
  description: string
  /** 대표 이미지로 쓸 images 파트의 순번 */
  thumbnailIndex: number
  aiExtracted: boolean
  withdrawalRestrictionReasonId: Id | null
  restrictionEvidenceUrl: string | null
}

/** WDR-01 활성 청약철회 제한 사유 */
export interface WithdrawalRestrictionReason {
  id: Id
  code: string
  description: string
  name: string
  requiresEvidence: boolean
}

/* ---------- AI-01 이미지 기반 상품 정보 추출 ---------- */

export interface AiField<TValue> {
  value: TValue
  /** 0~1. 낮으면 화면에서 검수 안내를 띄웁니다. */
  confidence: number
}

export interface AiExtraction {
  status: 'COMPLETED' | 'PARTIAL' | 'FAILED'
  fields: {
    name?: AiField<string>
    brand?: AiField<string>
    categoryId?: AiField<string>
    modelName?: AiField<string>
    color?: AiField<string>
    price?: AiField<number>
    description?: AiField<string>
  }
  warnings: string[]
}

/** 이 값보다 낮으면 "확인이 필요합니다" 안내를 띄웁니다. */
export const AI_CONFIDENCE_THRESHOLD = 0.85
