/** 공통 푸터 (.foot) */
export function SiteFooter({ compact = false }: { compact?: boolean }) {
  return (
    <footer className="foot">
      <div className="inner">
        <span>공구한 · 공동구매 플랫폼</span>
        {!compact && (
          <>
            <span>이용약관</span>
            <span>개인정보처리방침</span>
          </>
        )}
      </div>
    </footer>
  )
}
