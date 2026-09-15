/** 상품 등록·수정 화면이 함께 쓰는 입력 상태 */
export interface ProductFormState {
  name: string
  brand: string
  modelName: string
  color: string
  categoryId: string
  /** 숫자만 남긴 문자열. 표시할 때만 콤마를 붙입니다. */
  price: string
  description: string
  restricted: 'NONE' | 'RESTRICTED'
  reasonId: string
  evidenceUrl: string
}

export const EMPTY_PRODUCT_FORM: ProductFormState = {
  name: '',
  brand: '',
  modelName: '',
  color: '',
  categoryId: '',
  price: '',
  description: '',
  restricted: 'NONE',
  reasonId: '',
  evidenceUrl: '',
}

/** 필수 항목이 모두 찼는지 (이미지 여부는 화면마다 달라 여기서 보지 않습니다) */
export function isProductFormReady(form: ProductFormState): boolean {
  return (
    form.name.trim() !== '' &&
    form.description.trim() !== '' &&
    form.categoryId !== '' &&
    Number(form.price) > 0 &&
    (form.restricted === 'NONE' || form.reasonId !== '')
  )
}

/**
 * 선택 입력을 요청 본문에 담을 때 씁니다.
 *
 * 비워 둔 칸을 빈 문자열로 보내면 DB 에 ''  이 저장되어 "적지 않음" 과
 * 구분되지 않습니다. 공백만 남은 입력도 같이 털어냅니다.
 */
export function optionalText(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}
