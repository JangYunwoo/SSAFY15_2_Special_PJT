import { ApiError } from '@/shared/api/errors'

/**
 * 찜한 건을 서버가 "없다"고 답한 경우인가.
 *
 * 찜은 브라우저에만 있고 서버는 모릅니다. 그래서 서버에 없는 id 가 남아 있을 수
 * 있습니다. 공동구매가 지워졌거나, 다른 환경에서 찜한 id 를 다른 DB 를 보는
 * 화면에서 열었을 때입니다. 둘 다 장애가 아니라 "그 건이 이제 없다" 입니다.
 */
export function isMissingGroupBuy(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}

/** 찜 목록 화면이 무엇을 보여줄지. */
export type LikesView = 'empty' | 'loading' | 'error' | 'list'

/**
 * 표시할 화면을 고릅니다. 조회는 하지 않고 결정만 합니다.
 *
 * 예전에는 한 건도 못 받으면 무조건 오류 화면을 띄웠습니다. 서버에 없는 id 만
 * 남은 사용자에게는 찜 버튼을 누를 때마다 오류가 떠서, 고장난 것처럼 보였습니다.
 * 사라진 건만 남은 것과 실제로 못 불러온 것은 다르므로 여기서 가릅니다.
 */
export function likesView(input: {
  likedCount: number
  loadedCount: number
  pending: boolean
  errors: readonly unknown[]
}): LikesView {
  if (input.likedCount === 0) return 'empty'
  if (input.loadedCount > 0) return 'list'
  if (input.pending) return 'loading'

  // 하나도 못 받았을 때만 원인을 가릅니다. 404 뿐이면 찜해 둔 건이 이제 없는
  // 것이고, 그 밖의 오류(네트워크·5xx)는 실제로 못 불러온 것이라 그대로 알립니다.
  return input.errors.some((error) => !isMissingGroupBuy(error)) ? 'error' : 'empty'
}

/** 오류 화면에 보여줄 오류. 사라진 건(404)은 제외합니다. */
export function firstRealError(errors: readonly unknown[]): unknown {
  return errors.find((error) => !isMissingGroupBuy(error))
}
