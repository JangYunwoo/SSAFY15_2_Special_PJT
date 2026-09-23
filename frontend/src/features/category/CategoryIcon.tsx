/**
 * 카테고리 아이콘.
 *
 * 아이콘 자산이 저장소에 없어 인라인 SVG 로 그립니다. 외부 아이콘 패키지를
 * 새로 들이면 번들과 라이선스를 함께 떠안는데, 스물한 개짜리 한 줄에는 과합니다.
 *
 * 카테고리 이름으로 찾습니다. id 로 묶으면 백엔드가 시드 번호를 바꿀 때마다
 * 아이콘이 엉뚱한 자리로 갑니다. 모르는 이름은 '기타' 모양으로 떨어지므로,
 * 카테고리가 늘어도 화면이 비지 않습니다.
 */
const PATHS: Record<string, string> = {
  디지털기기: '<rect x="7" y="2.5" width="10" height="19" rx="2"/><line x1="10.5" y1="18.5" x2="13.5" y2="18.5"/>',
  생활가전: '<rect x="4" y="3" width="16" height="18" rx="2"/><circle cx="12" cy="13.5" r="4"/><line x1="7" y1="6.5" x2="9" y2="6.5"/>',
  '가구/인테리어': '<path d="M4 17v-6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v6"/><rect x="3" y="17" width="18" height="3.5" rx="1"/>',
  '생활/주방': '<path d="M5 9h14v7a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3z"/><line x1="2.5" y1="11" x2="5" y2="11"/><line x1="19" y1="11" x2="21.5" y2="11"/>',
  유아동: '<circle cx="12" cy="14" r="5"/><circle cx="6.8" cy="7.5" r="2.4"/><circle cx="17.2" cy="7.5" r="2.4"/>',
  유아도서: '<path d="M5 4h9a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z"/><line x1="8.5" y1="8.5" x2="13.5" y2="8.5"/>',
  여성의류: '<path d="M9 3l-2 4 2 1-2 12h10l-2-12 2-1-2-4z"/>',
  여성잡화: '<path d="M4 8h16l-1.2 12H5.2z"/><path d="M9 8V6a3 3 0 0 1 6 0v2"/>',
  '남성패션/잡화': '<path d="M8.5 3l3.5 2 3.5-2L20 6l-2.5 3v12h-11V9L4 6z"/>',
  '뷰티/미용': '<rect x="9" y="10" width="6" height="11" rx="1"/><path d="M10 10V5l4-2v7"/>',
  '스포츠/레저': '<circle cx="12" cy="12" r="8.5"/><line x1="12" y1="3.5" x2="12" y2="20.5"/><line x1="3.5" y1="12" x2="20.5" y2="12"/>',
  '취미/게임/음반': '<rect x="3" y="8" width="18" height="9" rx="4"/><line x1="7" y1="11" x2="7" y2="14"/><line x1="5.5" y1="12.5" x2="8.5" y2="12.5"/><circle cx="16.5" cy="12.5" r="1.1"/>',
  도서: '<path d="M5 4h14v16H7a2 2 0 0 1-2-2z"/><line x1="9" y1="4" x2="9" y2="20"/>',
  '티켓/교환권': '<path d="M3 8h18v3a2 2 0 0 0 0 4v3H3v-3a2 2 0 0 0 0-4z"/><line x1="13" y1="9.5" x2="13" y2="18"/>',
  e쿠폰: '<rect x="3" y="7" width="18" height="10" rx="2"/><line x1="9" y1="14.5" x2="15" y2="9.5"/><circle cx="9.5" cy="10.5" r="1"/><circle cx="14.5" cy="13.5" r="1"/>',
  농산물: '<path d="M20 4c-8 0-14 5-14 12 7 0 14-5 14-12z"/><line x1="3.5" y1="20.5" x2="9.5" y2="14.5"/>',
  가공식품: '<ellipse cx="12" cy="6" rx="7" ry="2.5"/><path d="M5 6v12c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5V6"/>',
  건강기능식품: '<path d="M14.5 3.5a4.95 4.95 0 0 1 7 7l-8 8a4.95 4.95 0 0 1-7-7z"/><line x1="10" y1="8" x2="17" y2="15"/>',
  반려동물용품: '<circle cx="7.2" cy="9.2" r="1.9"/><circle cx="12" cy="7" r="1.9"/><circle cx="16.8" cy="9.2" r="1.9"/><path d="M12 12.5c-3 0-5 2-5 4.2S9 20.5 12 20.5s5-1.6 5-3.8-2-4.2-5-4.2z"/>',
  식물: '<line x1="12" y1="21" x2="12" y2="12"/><path d="M12 13c0-4 3-6.5 6.5-6.5C18.5 10.5 15.5 13 12 13z"/><path d="M12 16c0-3-2.5-5.5-5.5-5.5C6.5 13.5 9 16 12 16z"/>',
  기타: '<circle cx="6" cy="12" r="1.7"/><circle cx="12" cy="12" r="1.7"/><circle cx="18" cy="12" r="1.7"/>',
  전체: '<rect x="3.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.5"/>',
}

/*
 * dangerouslySetInnerHTML 을 쓰지만 넣는 값은 위 PATHS 상수뿐입니다. 사용자
 * 입력이나 서버 응답이 여기에 닿지 않습니다 — 카테고리 이름은 키 조회에만 쓰고,
 * 모르는 이름은 '기타' 로 떨어집니다. 도형이 rect·circle·line·ellipse·path 로
 * 섞여 있어 JSX 로 펼치면 같은 내용이 네 배로 길어집니다.
 */
export function CategoryIcon({ name }: { name: string }) {
  const paths = PATHS[name] ?? PATHS['기타'] ?? ''

  return (
    <svg
      viewBox="0 0 24 24"
      width="26"
      height="26"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      dangerouslySetInnerHTML={{ __html: paths }}
    />
  )
}



