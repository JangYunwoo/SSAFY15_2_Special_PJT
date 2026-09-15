import type { Id, IsoDateTime, PageParams } from '@/shared/api/types'

/**
 * NOTI-01 알림 (백엔드 `NotificationDtos.NotificationResponse`).
 *
 * `type` 을 좁은 유니온으로 두었다가 백엔드와 값이 하나도 겹치지 않는 것을
 * 확인했습니다. 프론트는 GROUP_BUY_FULFILLED 같은 여섯 가지를 적어 두었는데
 * 실제로 만들어지는 값은 SELLER_SANCTION_WARNING·SUSPENDED·THREE_STRIKE
 * 세 가지였습니다. 종류는 앞으로도 늘어날 자리라, 화면에서 쓰지도 않는 값을
 * 좁게 묶어 두면 늘어날 때마다 타입만 어긋납니다. 문자열로 받습니다.
 */
export interface Notification {
  id: Id
  /** 알림 종류. 지금은 화면에 쓰지 않고 기록용입니다. */
  type: string
  title: string
  body: string
  linkUrl: string | null
  /** 백엔드는 읽은 시각이 아니라 읽었는지 여부만 내려줍니다. */
  read: boolean
  createdAt: IsoDateTime
}

export type NotificationListParams = PageParams
