/**
 * 판매자 아바타에 쓸 한 글자.
 *
 * 그냥 charAt(0) 을 쓰면 "[DEMO] 테크 셀렉트" 가 "[" 로 나옵니다. 운영 데이터의
 * 판매자명 상당수가 대괄호 표기로 시작해서, 글자·숫자가 처음 나오는 지점을 찾습니다.
 *
 * 이름이 비어 있으면 빈 동그라미가 되므로 물음표를 둡니다.
 */
/** 이름 앞에 붙는 분류 표기: "[DEMO] ", "(주) " 같은 것 */
const LEADING_TAG = /^\s*(?:\[[^\]]*\]\s*|\([^)]*\)\s*)+/

export function sellerInitial(sellerName: string): string {
  // 표기를 떼고 봅니다. 그대로 두면 "[DEMO] 테크 셀렉트" 가 "D" 가 되어
  // DEMO 판매자들이 전부 같은 글자로 보입니다.
  const withoutTag = sellerName.replace(LEADING_TAG, '')
  // 표기를 떼면 아무것도 안 남는 이름("[]")은 원본에서 다시 찾습니다.
  const source = withoutTag.trim() === '' ? sellerName : withoutTag

  for (const character of source) {
    // 유니코드 기준. 한글·영문·숫자를 모두 글자로 봅니다.
    if (/[\p{L}\p{N}]/u.test(character)) return character.toUpperCase()
  }
  return '?'
}
