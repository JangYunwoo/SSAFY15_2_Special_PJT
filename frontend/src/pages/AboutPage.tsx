import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { percent } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'

const STEPS = [
  { step: 'STEP 1', title: '모집', desc: '목표 인원이 모일 때까지 참여자를 모읍니다.' },
  { step: 'STEP 2', title: '분할결제', desc: '참여자별로 1인 결제금액만 결제합니다.' },
  { step: 'STEP 3', title: '에스크로', desc: '결제 금액은 구매 확정 전까지 보관됩니다.' },
  { step: 'STEP 4', title: '배송', desc: '모집 종료 후 3~5일 내 발송됩니다.' },
  { step: 'STEP 5', title: '정산', desc: '구매 확정 후 판매자에게 정산됩니다.' },
] as const

export default function AboutPage() {
  useDocumentTitle('서비스 소개')

  return (
    <main className="wrap">
      <section className="section">
        <div className="sec-head">
          <div>
            <h2 className="sec-title">이렇게 진행됩니다</h2>
            <p className="sec-sub">모집부터 정산까지 5단계</p>
          </div>
        </div>

        <div className="how">
          {STEPS.map((item) => (
            <div key={item.step}>
              <div className="n">{item.step}</div>
              <div className="t">{item.title}</div>
              <div className="d">{item.desc}</div>
            </div>
          ))}
        </div>
      </section>

      <section className="section" style={{ paddingTop: 0 }}>
        <div className="split">
          <Panel title="안전장치" description="결제 금액을 지키는 기준">
            <DataRow label="목표 인원 미달 시">전액 자동 환불</DataRow>
            <DataRow label="참여 취소">모집 종료 전까지 가능</DataRow>
            <DataRow label="청약철회">수령 다음 날부터 7일 이내</DataRow>
            <DataRow label="에스크로 보관">구매확정 전까지</DataRow>
            <DataRow label="구매확정">
              배송완료 후 {POLICY_DEFAULTS.autoConfirmDays}일 경과 시 자동
            </DataRow>
            <DataRow label="판매자 정산">
              배송완료 후 {POLICY_DEFAULTS.settlementEligibleDays}일 경과 · 구매확정 조건
            </DataRow>
            <DataRow label="플랫폼 수수료">
              분담금의 {percent(POLICY_DEFAULTS.platformFeeRate)} · 판매자 부담
            </DataRow>
          </Panel>

          <Panel title="시작하기" description="지금 참여할 수 있는 공동구매">
            <p className="para">
              목표 인원이 모이면 공동구매가 성사되고,
              <br />
              모이지 않으면 결제는 자동으로 취소됩니다.
            </p>
            <ButtonLink block to="/group-buys">
              공동구매 둘러보기
            </ButtonLink>
          </Panel>
        </div>
      </section>
    </main>
  )
}
