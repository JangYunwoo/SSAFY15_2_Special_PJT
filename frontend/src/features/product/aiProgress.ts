/**
 * AI 분석이 보통 걸리는 시간(초).
 *
 * 운영 AI 컨테이너에서 같은 호출을 재현해 측정한 값이 이미지 한 장에 62~63초다.
 * 장수가 늘면 더 걸리므로 "안팎" 으로 안내하고, 이 시간을 넘기면 문구를 바꾼다.
 */
export const AI_EXPECTED_SECONDS = 60

/**
 * 분석 중 안내 문구.
 *
 * 1분 가까이 걸리는 작업이라 "분석하고 있습니다" 만 띄우면 멈춘 것과 구분되지
 * 않는다. 경과 초를 함께 보여주면 진행 중임이 드러나고, 예상 시간을 미리
 * 알려주면 기다릴지 판단할 수 있다.
 */
export function aiProgressText(elapsedSeconds: number): string {
  if (elapsedSeconds >= AI_EXPECTED_SECONDS) {
    return `아직 분석 중입니다 · ${String(elapsedSeconds)}초 경과 · 조금만 더 기다려 주세요`
  }
  if (elapsedSeconds <= 0) {
    return '이미지를 분석하고 있습니다 · 보통 1분 안팎 걸립니다'
  }
  return `이미지를 분석하고 있습니다 · ${String(elapsedSeconds)}초 경과 · 보통 1분 안팎 걸립니다`
}
