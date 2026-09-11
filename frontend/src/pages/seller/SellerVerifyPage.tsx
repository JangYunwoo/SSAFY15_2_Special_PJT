import { Navigate } from 'react-router'

import { useIsSeller } from '@/features/auth/sessionStore'
import { SiteFooter } from '@/app/layouts/SiteFooter'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { PendingBackendState } from '@/shared/ui/states'

/**
 * 판매자 전환 안내 (전자상거래법 제10조).
 *
 * 예전에는 이 화면이 본인인증 폼을 그리고, 인증번호를 서버로 보내지 않은 채
 * 세션의 isSeller 만 켜서 판매자센터에 들여보냈습니다. 하지만 백엔드는 JWT 의
 * isSeller 클레임으로 ROLE_SELLER 를 주므로(JwtAuthenticationFilter), 그렇게
 * 들어가도 `/sellers/**` 요청은 전부 403 이었습니다. 통과한 것처럼 보이지만
 * 아무것도 되지 않는 화면이라, 실제 전환 API 가 생길 때까지는 상태를 그대로
 * 밝힙니다.
 *
 * 필요한 백엔드: SEL-01·02(휴대폰 인증), SEL-05(판매자 프로필 생성).
 * `SellerProfileController` 는 현재 조회·수정(GET/PATCH /sellers/me)만 있습니다.
 */
export default function SellerVerifyPage() {
  useDocumentTitle('판매자 전환')

  const isSeller = useIsSeller()

  // 이미 전환된 계정이 주소로 들어오면 판매자센터로 보냅니다.
  if (isSeller) return <Navigate to="/seller" replace />

  return (
    <>
      <header className="gnb">
        <div className="inner">
          <span className="brand">공구한</span>
          <span className="badge">판매자센터</span>
        </div>
      </header>

      <main className="wrap">
        <section className="section" style={{ paddingTop: 40 }}>
          <div className="sec-head">
            <div>
              <h2 className="sec-title">판매자 전환</h2>
              <p className="sec-sub">판매자센터는 판매자로 전환한 계정만 이용할 수 있습니다.</p>
            </div>
          </div>

          <div className="split">
            <div>
              <Panel title="전환 신청" description="본인인증 후 판매자 프로필을 등록합니다">
                {BACKEND_FEATURES.sellerOnboarding ? null : (
                  <PendingBackendState>
                    휴대폰 본인인증(SEL-01·02)과 판매자 프로필 등록(SEL-05) 엔드포인트가 아직
                    없습니다. 그때까지 판매자 계정은 DB에 직접 등록해야 하며, 로그인 응답의
                    <code> actor.isSeller </code>가 true인 계정만 판매자센터에 들어갈 수 있습니다.
                  </PendingBackendState>
                )}

                <ButtonLink variant="ghost" block className="mt16" to="/">
                  공동구매 둘러보기
                </ButtonLink>
              </Panel>
            </div>

            <aside>
              <Panel title="본인인증이 필요한 이유" description="전자상거래법 제10조">
                <div className="guide mt16">
                  <div className="guide-head">확인하는 항목</div>
                  <ul>
                    <li>판매자의 실명과 연락처를 확인합니다.</li>
                    <li>확인된 명의로만 정산 계좌를 등록할 수 있습니다.</li>
                    <li>인증은 판매자센터 최초 진입 시 한 번만 진행합니다.</li>
                    <li>입력한 정보는 본인확인기관에만 전달되며 공구한은 저장하지 않습니다.</li>
                  </ul>
                </div>
                <p className="small muted mt16" style={{ marginBottom: 0 }}>
                  만 19세 미만은 판매자로 등록할 수 없습니다. 법인 사업자는 인증 후 사업자등록번호를
                  추가로 등록해야 합니다.
                </p>
              </Panel>

              <Panel className="mt16" title="전환 후 이용할 수 있는 기능" description="판매자센터">
                <DataRow label="상품 관리">등록 · 수정 · 삭제 (PRD-01~05)</DataRow>
                <DataRow label="공동구매">생성 · 참여자 현황 (GRP-03~09)</DataRow>
                <DataRow label="배송">송장 등록 · 상태 변경 (DLV-01~04)</DataRow>
                <DataRow label="반품">승인 · 거부 · 수거 확인 (RTN-05~08)</DataRow>
              </Panel>
            </aside>
          </div>
        </section>
      </main>

      <SiteFooter compact />
    </>
  )
}
