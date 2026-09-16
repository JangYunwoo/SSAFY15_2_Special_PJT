import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { percent } from '@/shared/lib/format'
import { useBodyClass } from '@/shared/lib/hooks/useBodyClass'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'

/**
 * 참여자가 결제 전에 제일 먼저 궁금해하는 세 가지입니다.
 * 원래는 "안전장치" 표 일곱 줄 안에 수수료·정산 같은 부속 정보와 같은 크기로
 * 들어가 있어서 눈에 띄지 않았습니다.
 */
const POINTS = [
  {
    key: '인원이 안 모이면',
    value: '전액 자동 환불',
    desc: '목표 인원을 못 채우면 공동구매가 취소되고 결제 금액이 그대로 돌아갑니다.',
  },
  {
    key: '결제한 돈은',
    value: '구매확정 전까지 보관',
    desc: '판매자에게 바로 넘어가지 않습니다. 물건을 받고 확인할 때까지 플랫폼이 들고 있습니다.',
  },
  {
    key: '참여자가 내는 건',
    value: '분담금과 배송비뿐',
    desc: `플랫폼 수수료 ${percent(POLICY_DEFAULTS.platformFeeRate)}는 판매자가 부담합니다.`,
  },
] as const

const STEPS = [
  { step: 'STEP 1', title: '모집', desc: '목표 인원이 모일 때까지 참여자를 모읍니다.' },
  { step: 'STEP 2', title: '분할결제', desc: '참여자별로 1인 결제금액만 결제합니다.' },
  { step: 'STEP 3', title: '에스크로', desc: '결제 금액은 구매 확정 전까지 보관됩니다.' },
  { step: 'STEP 4', title: '배송', desc: '모집 종료 후 3~5일 내 발송됩니다.' },
  { step: 'STEP 5', title: '정산', desc: '구매 확정 후 판매자에게 정산됩니다.' },
] as const

export default function AboutPage() {
  useDocumentTitle('서비스 소개')
  // 배경을 한 톤 눌러 흰 카드가 떠 보이게 합니다.
  useBodyClass('tone-soft')

  return (
    <main className="wrap">
      <section className="about-lead">
        <h1>
          혼자 사기엔 비싼 물건,
          <br />
          <b>여럿이 모여</b> 나눠 삽니다
        </h1>
        <p>
          같은 물건이 필요한 사람끼리 모여 한 번에 사면 1인당 가격이 내려갑니다. 공구한은 그 모집과
          결제를 대신 맡습니다. 결제한 돈은 물건을 받고 확인할 때까지 보관되고, 사람이 모이지
          않으면 전액 돌려드립니다.
        </p>
      </section>

      <ul className="points">
        {POINTS.map((item) => (
          <li key={item.value}>
            <span className="k">{item.key}</span>
            <span className="v">{item.value}</span>
            <span className="d">{item.desc}</span>
          </li>
        ))}
      </ul>

      <section className="section about-sec">
        <div className="sec-head center">
          <div>
            <h2 className="sec-title">이렇게 진행됩니다</h2>
            <p className="sec-sub">
              모집부터 정산까지 5단계 · 참여자가 하는 일은 STEP 1~3 까지입니다
            </p>
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

      <section className="section about-sec">
        <div className="sec-head center">
          <div>
            <h2 className="sec-title">자세한 기준</h2>
            <p className="sec-sub">기한과 부담 주체</p>
          </div>
        </div>

        <div className="split even">
          <Panel title="결제와 환불" description="돈이 언제 움직이는지">
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

          {/*
            에스크로를 내세우는 서비스인데 "문제가 생기면 어디에 말하나" 가 없었습니다.
            반품(RTN)과 이의제기(CFM-02)는 상황이 달라 고르는 기준부터 적습니다.
          */}
          <Panel title="문제가 생기면" description="상황에 따라 경로가 다릅니다">
            <DataRow label="못 받았거나 다른 상품이 왔을 때" hint="자동 구매확정이 멈춥니다">
              이의제기
            </DataRow>
            <DataRow label="받았는데 하자가 있거나 마음이 바뀌었을 때">반품 신청</DataRow>
            <DataRow label="판매자 반품 승인 기한">
              {POLICY_DEFAULTS.returnApprovalBusinessDays}영업일
            </DataRow>
            <DataRow label="단순변심 반품비">본인 부담 · 최초 배송비는 환불 제외</DataRow>
            <DataRow label="판매자 귀책 반품비">판매자 부담 · 배송비까지 전액 환불</DataRow>
          </Panel>
        </div>
      </section>

      <section className="about-end">
        <h2>지금 참여할 수 있는 공동구매가 있습니다</h2>
        <p>
          목표 인원이 모이면 공동구매가 성사되고,
          <br />
          모이지 않으면 결제는 자동으로 취소됩니다.
        </p>
        <ButtonLink className="btn-arrow" to="/group-buys">
          공동구매 둘러보기
        </ButtonLink>
      </section>
    </main>
  )
}
