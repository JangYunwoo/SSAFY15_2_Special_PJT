import { useMutation } from '@tanstack/react-query'

import { productApi } from '@/entities/product/api'
import { AI_CONFIDENCE_THRESHOLD, type AiExtraction, type AiField } from '@/entities/product/model'

/** AI 가 채운 값과 그 신뢰도를 화면에 함께 보여주기 위한 형태 */
export interface AiHint {
  /** AI 모듈이 신뢰도를 주지 않으면 null */
  confidence: number | null
  /** 임계값보다 낮거나 신뢰도를 알 수 없어 판매자 확인이 필요한 항목 */
  needsReview: boolean
}

export type AiHints = Partial<Record<keyof AiExtraction['fields'], AiHint>>

export interface AiExtractionResult {
  values: {
    name?: string
    brand?: string
    modelName?: string
    color?: string
    categoryId?: string
    price?: string
    description?: string
  }
  /** AI가 상품명을 확정하지 못했을 때의 후보. 화면에서 하나를 고르게 합니다. */
  nameCandidates: string[]
  hints: AiHints
  warnings: string[]
}

/**
 * AI-01. 이미지에서 상품 정보를 추출합니다.
 *
 * 결과는 저장 확정 데이터가 아니므로 폼의 초기값으로만 넣고,
 * 판매자가 검수한 뒤 PRD-01 로 등록할 때 aiExtracted=true 를 함께 보냅니다.
 */
export function useAiExtraction(onResult: (result: AiExtractionResult) => void) {
  return useMutation({
    mutationFn: (images: File[]) => productApi.extract(images),
    onSuccess: (extraction) => {
      onResult(toResult(extraction))
    },
  })
}

function toResult(extraction: AiExtraction): AiExtractionResult {
  const { fields } = extraction
  const hints: AiHints = {}

  for (const [key, field] of Object.entries(fields)) {
    // fields 에는 AiField 가 아닌 값도 섞여 있다(nameCandidates 는 문자열 배열).
    // 키 이름으로 걸러내면 필드가 늘 때마다 여기를 고쳐야 하고 서버가 보낸
    // null 도 걸러내지 못하므로, 모양으로 판단한다.
    if (!isAiField(field)) continue
    const confidence = typeof field.confidence === 'number' ? field.confidence : null
    hints[key as keyof AiExtraction['fields']] = {
      confidence,
      // 신뢰도를 모르면 검수를 권합니다. 모른다는 이유로 넘기면 확인 없이 등록됩니다.
      needsReview: confidence === null || confidence < AI_CONFIDENCE_THRESHOLD,
    }
  }

  return {
    values: {
      ...(fields.name && { name: fields.name.value }),
      ...(fields.brand && { brand: fields.brand.value }),
      ...(fields.modelName && { modelName: fields.modelName.value }),
      ...(fields.color && { color: fields.color.value }),
      ...(fields.categoryId && { categoryId: fields.categoryId.value }),
      ...(fields.price && { price: String(fields.price.value) }),
      ...(fields.description && { description: fields.description.value }),
    },
    nameCandidates: fields.nameCandidates ?? [],
    hints,
    warnings: extraction.warnings,
  }
}

/** 입력 아래에 붙일 안내 문구 */
export function aiHintText(hint: AiHint | undefined): string | undefined {
  if (hint === undefined) return undefined

  // 신뢰도를 모르면 숫자를 지어내지 않습니다. "신뢰도 0%" 는 AI 가 확신하지 못했다는
  // 뜻으로 읽히는데, 실제로는 측정값이 없을 뿐이라 정상 추출을 틀린 값처럼 보이게 합니다.
  if (hint.confidence === null) {
    return 'AI 추출 · 값을 확인해 주세요'
  }

  const confidence = `${String(Math.round(hint.confidence * 100))}%`
  return hint.needsReview
    ? `AI 추출 · 신뢰도 ${confidence} · 값을 확인해 주세요`
    : `AI 추출 · 신뢰도 ${confidence}`
}

/** 신뢰도를 가진 AI 추출 필드인지 모양으로 확인합니다. */
function isAiField(value: unknown): value is AiField<unknown> {
  // confidence 로 판단하지 않습니다. 신뢰도를 주지 않는 항목은 null 로 내려오고,
  // 서버 설정에 따라 키가 아예 빠질 수도 있어 값의 유무로만 봅니다.
  // nameCandidates 는 문자열 배열이라 여기서 걸러집니다.
  return typeof value === 'object' && value !== null && !Array.isArray(value) && 'value' in value
}
