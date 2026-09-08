import { useEffect } from 'react'

const SUFFIX = '공구한'

/** 라우트별 문서 제목. 목업의 <title> 규칙(`화면 · 공구한`)을 따릅니다. */
export function useDocumentTitle(title?: string): void {
  useEffect(() => {
    document.title = title ? `${title} · ${SUFFIX}` : SUFFIX
  }, [title])
}
