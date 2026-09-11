import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'

export default function NotFoundPage() {
  useDocumentTitle('페이지를 찾을 수 없습니다')

  return (
    <main className="wrap">
      <section className="result">
        <h2>페이지를 찾을 수 없습니다</h2>
        <p>주소가 바뀌었거나 삭제된 화면입니다.</p>
        <ButtonLink block to="/">
          공동구매 둘러보기
        </ButtonLink>
      </section>
    </main>
  )
}
