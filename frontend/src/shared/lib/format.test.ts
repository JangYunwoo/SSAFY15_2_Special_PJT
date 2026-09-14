import { describe, expect, it } from 'vitest'

import { clock, dday, groupBuyNo, maskName, phone, remaining, signedWon, won } from './format'

/**
 * 화면 어디서나 쓰는 표기 함수입니다. 시간이 들어가는 함수는 기준 시각을
 * 인자로 받도록 되어 있어 고정된 값으로 검증합니다.
 */
describe('금액 표기', () => {
  it('천 단위를 끊고 원을 붙입니다', () => {
    expect(won(1_040_000)).toBe('1,040,000원')
  })

  it('음수는 빼기 기호를 앞에 둡니다', () => {
    expect(signedWon(-10_000)).toBe('− 10,000원')
    expect(signedWon(10_000)).toBe('10,000원')
  })
})

describe('시간 표기', () => {
  const now = new Date('2026-09-14T00:00:00Z')

  it('남은 시간을 일·시간으로 끊어 보여줍니다', () => {
    expect(remaining('2026-09-17T12:00:00Z', now)).toBe('3일 12시간 남음')
    expect(remaining('2026-09-14T05:00:00Z', now)).toBe('5시간 남음')
    expect(remaining('2026-09-14T00:30:00Z', now)).toBe('30분 남음')
  })

  it('이미 지난 시각은 마감입니다', () => {
    expect(remaining('2026-09-13T23:59:00Z', now)).toBe('마감')
  })

  it('D-day 는 남은 일수를 올림해 셉니다', () => {
    expect(dday('2026-09-18T00:00:00Z', now)).toBe('D-4')
    expect(dday('2026-09-14T00:00:00Z', now)).toBe('D-DAY')
    expect(dday('2026-09-13T00:00:00Z', now)).toBe('기한 종료')
    expect(dday(null, now)).toBe('')
  })

  it('결제 슬롯 타이머는 분:초로 표기하고 음수는 0 으로 봅니다', () => {
    expect(clock(595)).toBe('09:55')
    expect(clock(-1)).toBe('00:00')
  })
})

describe('개인정보 표기', () => {
  it('휴대폰 번호에 하이픈을 넣습니다', () => {
    expect(phone('01012345678')).toBe('010-1234-5678')
    expect(phone('0212345678')).toBe('021-234-5678')
  })

  it('이름은 가운데를 가립니다', () => {
    expect(maskName('김')).toBe('김')
    expect(maskName('이연')).toBe('이*')
    expect(maskName('이수연')).toBe('이*연')
    expect(maskName('남궁수연')).toBe('남**연')
  })
})

describe('groupBuyNo', () => {
  it('네 자리로 채워 # 를 붙입니다', () => {
    expect(groupBuyNo('24')).toBe('#0024')
    expect(groupBuyNo(1024)).toBe('#1024')
    expect(groupBuyNo('123456')).toBe('#123456')
  })
})
