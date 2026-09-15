import type { Penalty } from '@/entities/settlement/model'
import type { Id, IsoDateTime, Krw, PageParams } from '@/shared/api/types'
import type { StateTone } from '@/shared/ui/StateChip'

/**
 * 관리자(운영) 도메인 타입.
 *
 * 관리자 API 는 `/admin/**` 아래에 있고 SecurityConfig 가 ROLE_ADMIN 으로 막습니다.
 * 계정도 `users` 가 아니라 `admins` 테이블이라 로그인 경로부터 다릅니다
 * (AUTH-05 `POST /auth/admins/login`).
 *
 * 판매자 화면과 달리 소유권 검사가 없습니다. 플랫폼 전체를 보는 화면이라
 * 목록에 다른 판매자·구매자의 건이 그대로 섞여 옵니다.
 */

interface Display {
  label: string
  tone: StateTone
}

/**
 * 표기 맵에서 안전하게 꺼냅니다.
 *
 * 관리자 응답의 상태·사유는 enum 이 아니라 문자열로 옵니다(`RefundResponse.status`
 * 등). 백엔드에 값이 하나 늘었을 때 화면이 빈칸이 되거나 터지는 대신, 모르는
 * 값은 코드 그대로 무채색 칩으로 보여줍니다 — 운영자가 "처음 보는 상태" 임을
 * 알아채는 편이 조용히 사라지는 것보다 낫습니다.
 */
function lookup<TKey extends string>(
  table: Record<TKey, Display>,
  value: string,
): Display {
  return (table as Record<string, Display | undefined>)[value] ?? { label: value, tone: 'neutral' }
}

/**
 * 관리자 응답의 시각을 UTC 로 해석합니다.
 *
 * 다른 도메인 DTO 는 `LocalDateTime` 을 `atOffset(ZoneOffset.UTC).toString()` 으로
 * 바꿔 `...Z` 를 붙여 보내지만, `AdminDtos`(ADM-03·05·07)는 `LocalDateTime` 을
 * 그대로 직렬화해 `2026-09-15T12:34:56` 처럼 오프셋이 없습니다. 그대로
 * `new Date()` 에 넣으면 브라우저가 이것을 **현지 시각**으로 읽어 KST 에서 9시간
 * 밀립니다. 서버가 UTC 로 저장하므로 오프셋이 없으면 `Z` 를 붙여 해석합니다.
 *
 * 백엔드가 다른 DTO 처럼 ISO UTC 로 바꿔주면 이 함수는 입력을 그대로 돌려줍니다
 * (이미 `Z` 나 오프셋이 있으면 건드리지 않습니다).
 */
export function asUtc(value: string | null | undefined): IsoDateTime | null {
  if (!value) return null
  return /(?:Z|[+-]\d{2}:?\d{2})$/.test(value) ? value : `${value}Z`
}

/* =========================================================
   ESC-03 에스크로 집계
   ========================================================= */

/**
 * 플랫폼이 지금 얼마를 들고 있는지 (백엔드 `EscrowAdminSummaryResponse`).
 *
 * `heldAmount + awaitingSettlementAmount` 가 플랫폼이 실제로 책임지는 금액입니다.
 * 앞은 아직 주인이 정해지지 않은 돈, 뒤는 판매자 몫으로 확정됐지만 아직 안 나간 돈입니다.
 */
export interface EscrowSummary {
  heldCount: number
  heldAmount: Krw
  awaitingSettlementCount: number
  awaitingSettlementAmount: Krw
  settledCount: number
  settledAmount: Krw
  refundedCount: number
  refundedAmount: Krw
  totalCount: number
  totalAmount: Krw
  currency: string
}

/* =========================================================
   ADM-03·04 운영 정책
   ========================================================= */

/** `platform_policies` 한 행 (백엔드 `AdminDtos.PolicyResponse`). */
export interface Policy {
  policyKey: string
  policyValue: string
  description: string | null
  /** 마지막으로 바꾼 관리자 ID. 시드값 그대로면 null 입니다. */
  updatedBy: number | null
  updatedAt: string | null
}

/**
 * 정책 키 묶음. 백엔드 `PolicyKey` enum 순서를 그대로 따릅니다.
 *
 * 화면에서 쓰는 것은 묶음뿐이고 목록 자체는 서버 응답을 그대로 그립니다 —
 * 여기에 없는 키가 생겨도 "기타" 로 흘러가 화면에서 사라지지 않습니다.
 */
export const POLICY_GROUPS: { title: string; hint: string; keys: readonly string[] }[] = [
  {
    title: '수수료 · 인원',
    hint: '정산 금액과 공동구매 생성 조건을 정합니다.',
    keys: ['PLATFORM_FEE_RATE', 'MIN_TARGET_PARTICIPANTS'],
  },
  {
    title: '결제 슬롯',
    hint: '결제를 붙잡아 두는 시간입니다. 지나면 슬롯이 풀립니다.',
    keys: ['SLOT_TTL_CARD_SEC', 'SLOT_TTL_CRYPTO_SEC'],
  },
  {
    title: '코인 결제',
    hint: '환율 고시 유효시간과 체인 스캔 주기입니다.',
    keys: [
      'QUOTE_TTL_SEC',
      'CRYPTO_BUFFER_RATE',
      'CHAIN_SCAN_INTERVAL_MS',
      'CHAIN_SCAN_BATCH_BLOCKS',
    ],
  },
  {
    title: '기한 · 제재',
    hint: '자동 확정·정산·반품 기한입니다. 바꾸면 진행 중인 건에도 바로 반영됩니다.',
    keys: [
      'SHIPPING_DEADLINE_DAYS',
      'FAKE_INVOICE_DETECT_DAYS',
      'AUTO_CONFIRM_DAYS',
      'WITHDRAWAL_PERIOD_DAYS',
      'SETTLEMENT_ELIGIBLE_DAYS',
      'RETURN_APPROVE_DAYS',
      'RETURN_AUTO_REFUND_DAYS',
      'CARD_BILLING_CUTOFF_DAYS',
      'PENALTY_LIMIT',
      'SETTLEMENT_BATCH_HOUR',
    ],
  },
  {
    title: 'SSAFY 금융망',
    hint: '외부 연동 식별자입니다. 잘못 바꾸면 결제·정산이 전부 실패합니다.',
    keys: [
      'SSAFY_MERCHANT_ID',
      'SSAFY_PLATFORM_ACCOUNT_NO',
      'SSAFY_INSTITUTION_CODE',
      'SSAFY_FINTECH_APP_NO',
    ],
  },
]

/* =========================================================
   ADM-05·07 운영 로그
   ========================================================= */

/** 외부 금융 API 호출 이력 한 건 (백엔드 `SsafyApiLog`). */
export interface ExternalApiLog {
  logId: number
  apiServiceCode: string
  institutionTxNo: string | null
  targetType: string | null
  targetId: number | null
  /** SSAFY 금융망 응답코드. `H0000` 이 성공입니다. */
  responseCode: string | null
  responseMessage: string | null
  calledAt: string | null
}

/** 관리자 조치 감사 로그 한 건 (백엔드 `AuditLog`). */
export interface AuditLog {
  logId: number
  actorType: string
  actorId: number | null
  targetType: string | null
  targetId: number | null
  action: string
  /** 바뀌기 전 값 JSON. 정책 변경이면 `{"value":"..."}` 입니다. */
  beforeJson: string | null
  afterJson: string | null
  createdAt: string | null
}

/** GRP 모집 마감 배치 실행 결과 (백엔드 `GroupBuyJudgementResult`). */
export interface GroupBuyJudgement {
  processedCount: number
  fulfilledCount: number
  failedCount: number
}

/* =========================================================
   RFD-03·04 환불
   ========================================================= */

export const REFUND_STATUS = ['PROCESSING', 'COMPLETED', 'FAILED'] as const
export type RefundStatus = (typeof REFUND_STATUS)[number]

export const REFUND_METHOD = ['CARD_CANCEL', 'ACCOUNT_TRANSFER', 'KAKAOPAY', 'CRYPTO'] as const
export type RefundMethod = (typeof REFUND_METHOD)[number]

/**
 * 환불 한 건 (백엔드 `RefundResponse`).
 *
 * 계좌번호는 서버가 뒤 4자리만 남겨 보냅니다. 코인 환불의 주소·수량·해시는
 * 온체인 공개 정보라 마스킹하지 않습니다.
 */
export interface Refund {
  refundId: Id
  participationId: Id | null
  returnId: Id | null
  productName: string | null
  amount: Krw
  /** RECRUIT_FAILED · PARTICIPATION_CANCEL · SLOT_EXPIRED · RETURN · SELLER_FAULT · UNMATCHED_DEPOSIT */
  reasonType: string
  refundMethod: string
  status: string
  toAccountNo: string | null
  toAddress: string | null
  /** 소수 18자리라 문자열입니다. Number 로 바꾸면 값이 뭉개집니다. */
  assetAmount: string | null
  txHash: string | null
  institutionTxNo: string | null
  failReason: string | null
  createdAt: IsoDateTime | null
  processedAt: IsoDateTime | null
}

export interface RefundListParams extends PageParams {
  status?: RefundStatus
}

export const REFUND_STATUS_DISPLAY: Record<RefundStatus, Display> = {
  PROCESSING: { label: '환불 진행중', tone: 'live' },
  COMPLETED: { label: '환불 완료', tone: 'ok' },
  FAILED: { label: '환불 실패', tone: 'alert' },
}

export const REFUND_METHOD_LABEL: Record<RefundMethod, string> = {
  CARD_CANCEL: '카드 취소',
  ACCOUNT_TRANSFER: '계좌 이체',
  KAKAOPAY: '카카오페이',
  CRYPTO: '코인',
}

export const REFUND_REASON_LABEL: Record<string, string> = {
  RECRUIT_FAILED: '모집 실패',
  PARTICIPATION_CANCEL: '참여 취소',
  SLOT_EXPIRED: '결제 슬롯 만료',
  RETURN: '반품',
  SELLER_FAULT: '판매자 귀책',
  UNMATCHED_DEPOSIT: '미매칭 입금',
}

export const refundStatusDisplay = (status: string): Display =>
  lookup(REFUND_STATUS_DISPLAY, status)

export const refundMethodLabel = (method: string): string =>
  (REFUND_METHOD_LABEL as Record<string, string | undefined>)[method] ?? method

export const refundReasonLabel = (reason: string): string =>
  REFUND_REASON_LABEL[reason] ?? reason

/* =========================================================
   PEN-02~06 판매자 제재
   ========================================================= */

export const PENALTY_STATUS = ['CANDIDATE', 'CONFIRMED', 'DISMISSED', 'REVOKED'] as const
export type PenaltyStatus = (typeof PENALTY_STATUS)[number]

export interface PenaltyListParams extends PageParams {
  status?: PenaltyStatus
}

/**
 * PEN-03 위반 후보 상세 (백엔드 `PenaltyQueryService.PenaltyDetailResult`).
 * 같은 판매자의 확정 이력을 함께 돌려줍니다 — 이번이 몇 번째인지에 따라 처분이 달라집니다.
 */
export interface PenaltyDetail {
  penalty: Penalty
  confirmedHistory: Penalty[]
  /** 이 횟수에 도달하면 판매가 정지됩니다 (정책 `PENALTY_LIMIT`). */
  penaltyLimit: number
}

export const PENALTY_STATUS_DISPLAY: Record<PenaltyStatus, Display> = {
  CANDIDATE: { label: '검토 대기', tone: 'hold' },
  CONFIRMED: { label: '위반 확정', tone: 'alert' },
  DISMISSED: { label: '기각', tone: 'neutral' },
  REVOKED: { label: '확정 취소', tone: 'back' },
}

export const PENALTY_REASON_LABEL: Record<string, string> = {
  NO_INVOICE: '송장 미등록',
  FAKE_INVOICE: '허위 송장',
  ITEM_MISMATCH: '상품 불일치',
  WITHDRAWAL: '판매 철회',
  RETURN_REFUSAL: '반품 부당 거부',
  RETURN_NEGLECT: '반품 처리 방치',
}

export const penaltyStatusDisplay = (status: string): Display =>
  lookup(PENALTY_STATUS_DISPLAY, status)

export const penaltyReasonLabel = (reason: string): string =>
  PENALTY_REASON_LABEL[reason] ?? reason

/* =========================================================
   청약철회 제한 심사
   ========================================================= */

/**
 * 심사 대기 중인 상품 한 건 (백엔드 `ProductResponse` 중 심사에 필요한 부분).
 *
 * 판매자가 "청약철회 제한" 을 걸어 올린 상품입니다. 승인하면 구매자가 그 상품을
 * 단순변심으로 반품할 수 없게 되므로 근거 자료를 보고 판단합니다.
 */
export interface RestrictionProduct {
  productId: Id
  sellerId: Id
  name: string
  categoryName: string
  price: Krw
  withdrawalRestrictionReason: string | null
  restrictionEvidenceUrl: string | null
  restrictionReviewStatus: string
  createdAt: IsoDateTime | null
}

/** 심사 결과로 보낼 수 있는 값. `NONE`·`PENDING` 은 관리자가 고르는 값이 아닙니다. */
export const RESTRICTION_DECISION = ['APPROVED', 'REJECTED'] as const
export type RestrictionDecision = (typeof RESTRICTION_DECISION)[number]

export const RESTRICTION_REVIEW_DISPLAY: Record<string, Display> = {
  NONE: { label: '제한 없음', tone: 'neutral' },
  PENDING: { label: '심사 대기', tone: 'hold' },
  APPROVED: { label: '승인', tone: 'ok' },
  REJECTED: { label: '반려', tone: 'alert' },
}

export const restrictionReviewDisplay = (status: string): Display =>
  RESTRICTION_REVIEW_DISPLAY[status] ?? { label: status, tone: 'neutral' }

/* =========================================================
   관리자 반품 검토
   ========================================================= */

/** 관리자 반품 처리 결정. 기각에는 사유가 필수입니다 (백엔드에서 400). */
export const RETURN_DECISION = ['APPROVE', 'REJECT'] as const
export type ReturnDecision = (typeof RETURN_DECISION)[number]
