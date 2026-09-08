import { useMutation } from '@tanstack/react-query'

import { productApi } from '@/entities/product/api'
import { AI_CONFIDENCE_THRESHOLD, type AiExtraction } from '@/entities/product/model'

/** AI 가 채운 값과 그 신뢰도를 화면에 함께 보여주기 위한 형태 */
export interface AiHint {
  confidence: number
  /** 임계값보다 낮아 판매자 확인이 필요한 항목 */
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
    hints[key as keyof AiExtraction['fields']] = {
      confidence: field.confidence,
      needsReview: field.confidence < AI_CONFIDENCE_THRESHOLD,
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
    hints,
    warnings: extraction.warnings,
  }
}

/** 입력 아래에 붙일 안내 문구 */
export function aiHintText(hint: AiHint | undefined): string | undefined {
  if (hint === undefined) return undefined
  const confidence = `${String(Math.round(hint.confidence * 100))}%`
  return hint.needsReview
    ? `AI 추출 · 신뢰도 ${confidence} · 값을 확인해 주세요`
    : `AI 추출 · 신뢰도 ${confidence}`
}
