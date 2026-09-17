import logoWordmark from '@/assets/logo-wordmark.svg'

/**
 * 서비스 로고. 폰트가 아니라 윤곽선 SVG 라 웹폰트 로딩과 무관하게 항상 같은 모양입니다.
 *
 * 크기는 감싸는 쪽의 `.brand img` 규칙으로 정합니다. 헤더 27px, 모바일 헤더와
 * 푸터 22px, 로그인 화면 34px 입니다.
 */
export function BrandMark() {
  return <img src={logoWordmark} alt="공구한" />
}
