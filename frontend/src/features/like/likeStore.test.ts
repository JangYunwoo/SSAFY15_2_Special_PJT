import { describe, expect, it } from 'vitest'

import { selectLikedIds } from './likeStore'

/**
 * 이 파일이 막는 것은 React error #185(Maximum update depth exceeded)다.
 *
 * zustand 는 useSyncExternalStore 로 스냅샷을 Object.is 로 비교한다. 셀렉터가
 * 호출마다 새 배열을 만들면 값이 같아도 "바뀌었다" 로 판정되어 무한 리렌더에
 * 빠지고 페이지 전체가 죽는다. 찜한 것이 하나도 없는 사용자에게만 재현되어
 * 오래 못 찾았다.
 */
describe('찜 목록 셀렉터의 참조 안정성', () => {
  it('찜이 없으면 호출할 때마다 같은 배열을 돌려준다', () => {
    expect(selectLikedIds({}, 'user-1')).toBe(selectLikedIds({}, 'user-1'))
  })

  it('비로그인도 같은 배열을 돌려준다', () => {
    expect(selectLikedIds({}, null)).toBe(selectLikedIds({}, null))
  })

  it('비로그인과 찜 없는 사용자가 같은 빈 배열을 공유한다', () => {
    expect(selectLikedIds({}, null)).toBe(selectLikedIds({ other: ['1'] }, 'user-1'))
  })

  it('다른 사용자의 찜이 있어도 내 것이 없으면 같은 배열을 돌려준다', () => {
    const byUser = { 'user-2': ['10', '11'] }
    expect(selectLikedIds(byUser, 'user-1')).toBe(selectLikedIds(byUser, 'user-1'))
  })

  it('찜이 있으면 저장된 배열을 그대로 돌려준다', () => {
    const mine = ['10', '11']
    const byUser = { 'user-1': mine }
    expect(selectLikedIds(byUser, 'user-1')).toBe(mine)
  })
})

describe('찜 목록 내용', () => {
  it('내 것만 돌려준다', () => {
    const byUser = { 'user-1': ['10'], 'user-2': ['20'] }
    expect(selectLikedIds(byUser, 'user-1')).toEqual(['10'])
  })

  it('비로그인은 빈 목록', () => {
    expect(selectLikedIds({ 'user-1': ['10'] }, null)).toEqual([])
  })
})
