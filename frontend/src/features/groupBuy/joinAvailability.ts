import type { GroupBuyStatus } from '@/entities/model'

/** 참여 버튼이 필요한 값만 받습니다. 상세 응답 전체를 받으면 테스트가 무거워집니다. */
export interface JoinAvailabilityInput {
  status: GroupBuyStatus
  paidCount: number
  targetCount: number
  sellerSuspended: boolean
}

export interface JoinAvailability {
  canJoin: boolean
  /** 버튼에 그대로 넣는 문구 */
  label: string
}

/**
 * 참여 가능 여부와 버튼 문구.
 *
 * 기존에는 막힌 이유를 가리지 않고 "참여할 수 없습니다" 하나로 보여줬습니다.
 * 모집이 끝난 것인지, 자리가 찬 것인지, 취소된 건인지 구분되지 않아 버튼이
 * 고장난 것처럼 보였습니다. 이유를 알면 기다릴지 다른 건을 볼지 판단할 수 있습니다.
 *
 * <p>판매자 제재는 "제재" 라고 쓰지 않습니다. 구매자에게 필요한 정보는 지금
 * 살 수 없다는 사실이고, 판매자의 위반 이력을 공개 화면에서 알릴 이유가 없습니다.
 *
 * <p>모집 마감 <b>시각</b>은 보지 않습니다. 브라우저 시계가 어긋나면 아직 열려
 * 있는 건을 막아버립니다. 마감 판정은 서버 스케줄러가 status 로 내리고, 참여
 * 가능 여부의 최종 판단도 결제 준비(PAY-01)에서 서버가 다시 합니다.
 */
export function joinAvailability(input: JoinAvailabilityInput): JoinAvailability {
  // 정지된 판매자의 건은 서버가 새 결제를 거부합니다. 결제 화면까지 보내 놓고
  // 실패시키는 대신 여기서 막습니다.
  if (input.sellerSuspended) {
    return { canJoin: false, label: '현재 참여할 수 없는 상품입니다' }
  }

  switch (input.status) {
    case 'FULFILLED':
      return { canJoin: false, label: '모집이 완료되었습니다' }
    case 'FAILED':
      return { canJoin: false, label: '모집에 실패한 공동구매입니다' }
    case 'CANCELED':
      return { canJoin: false, label: '취소된 공동구매입니다' }
    case 'RECRUITING':
      break
  }

  if (input.paidCount >= input.targetCount) {
    return { canJoin: false, label: '모집 인원이 모두 찼습니다' }
  }

  return { canJoin: true, label: '공동구매 참여하기' }
}
