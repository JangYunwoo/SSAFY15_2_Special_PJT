import { Link } from 'react-router'

import { BrandMark } from '@/shared/ui/BrandMark'

/**
 * 공통 푸터 (.foot)
 *
 * 기본형은 연회색 라운드 카드입니다. compact 는 결제·판매자센터·관리자처럼
 * 일을 처리하는 화면에서 씁니다. 그 화면들은 일부러 메뉴를 감춰 이탈을
 * 줄이는 자리라(CheckoutLayout 주석 참고) 링크가 늘어난 큰 푸터를 넣으면
 * 그 의도와 어긋납니다. 그래서 한 줄짜리로 그대로 둡니다.
 */
export function SiteFooter({ compact = false }: { compact?: boolean }) {
  if (compact) {
    return (
      <footer className="foot foot-min">
        <div className="inner">
          <span>공구한 · 공동구매 플랫폼</span>
        </div>
      </footer>
    )
  }

  return (
    <footer className="foot">
      <div className="foot-card">
        <div className="foot-top">
          <div className="foot-brand">
            <span className="brand"><BrandMark /></span>
            <p>필요한 사람끼리 모여 더 싸게, 결제는 에스크로로 안전하게</p>
          </div>

          <div className="foot-col">
            <span className="foot-col-title">서비스</span>
            <nav className="foot-col-list" aria-label="서비스 바로가기">
              <Link to="/">둘러보기</Link>
              <Link to="/about">서비스 소개</Link>
              <Link to="/seller">판매자센터</Link>
            </nav>
          </div>

          <div className="foot-col">
            <span className="foot-col-title">이용안내</span>
            {/*
              연결할 페이지가 아직 없어 글자로만 둡니다. 없는 곳으로 가는
              링크를 만들면 누른 사람이 빈 화면을 봅니다. 페이지가 생기면
              <Link> 로 바꾸세요.
            */}
            <div className="foot-col-list">
              <span>이용약관</span>
              <span>개인정보처리방침</span>
            </div>
          </div>

          {/*
            레퍼런스의 Information·Contact 자리입니다. 사업자 등록번호·주소·
            대표자·문의 메일은 아직 정해진 값이 없어 비워 둡니다. 확인되면
            여기에 .foot-col 을 하나 더 넣으면 3열이 4열이 됩니다.
            통신판매업 신고번호처럼 실제로 받은 값만 적어야 하는 항목이라
            임의로 채우지 않았습니다.
          */}
        </div>

        <div className="foot-bottom">
          <span>© 2026 공구한</span>
          <span>SSAFY 15기 자율 프로젝트</span>
        </div>
      </div>
    </footer>
  )
}

