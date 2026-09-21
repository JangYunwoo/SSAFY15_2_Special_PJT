import { paymentApi } from '@/entities/payment/api'
import { isApiError } from '@/shared/api/errors'

/** 이미 종료된 슬롯만 정리 완료로 간주한다. 승인된 결제를 취소 성공으로 숨기지 않는다. */
export async function releasePaymentSlot(paymentId: string): Promise<void> {
  try {
    await paymentApi.abort(paymentId)
  } catch (error) {
    if (isApiError(error, 'PAYMENT_ALREADY_PROCESSED') ||
        isApiError(error, 'INVALID_STATE_TRANSITION') ||
        isApiError(error, 'PAYMENT_SLOT_EXPIRED')) {
      const current = await paymentApi.detail(paymentId)
      if (current.status === 'FAILED' || current.status === 'EXPIRED') return
    }
    throw error
  }
}
