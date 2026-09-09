import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import { GROUP_BUY_STATUS_DISPLAY } from '@/entities/model'
import type { GroupBuyDetail } from '@/entities/groupBuy/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { useIsSignedIn } from '@/features/auth/sessionStore'
import { GroupBuyGallery } from '@/features/groupBuy/GroupBuyGallery'
import { discountRate, POLICY_DEFAULTS } from '@/shared/config/policy'
import { date, number, percent, remaining, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { ProgressBar } from '@/shared/ui/ProgressBar'
import { QueryBoundary } from '@/shared/ui/states'

export default function GroupBuyDetailPage() {
  const { groupBuyId = '' } = useParams()
  const query = useQuery(groupBuyQueries.detail(groupBuyId))

  useDocumentTitle(query.data?.productName ?? '상품 상세')

  return (
    <main className="wrap">
      <QueryBoundary query={query}>{(detail) => <DetailView detail={detail} />}</QueryBoundary>
    </main>
  )
}

function DetailView({ detail }: { detail: GroupBuyDetail }) {
  const navigate = useNavigate()
  const isSignedIn = useIsSignedIn()

  const status = GROUP_BUY_STATUS_DISPLAY[detail.status]
  const discount = discountRate(detail.listPrice, detail.unitShareAmount)
  // 서버가 확정한 1인 결제금액을 그대로 씁니다 (GRP-02 personalPaymentAmount).
  const totalAmount = detail.personalPaymentAmount
  const isJoinable = detail.status === 'RECRUITING' && detail.paidCount < detail.targetCount

  const join = () => {
    if (!isSignedIn) {
      void navigate('/login', { state: { from: `/group-buys/${detail.id}` } })
      return
    }
    void navigate(`/checkout/${detail.id}/shipping`)
  }

  return (
    <>
      <div className="crumb">
        <button
          type="button"
          className="crumb-share"
          onClick={() => {
            void navigator.clipboard.writeText(window.location.href)
          }}
        >
          공유
        </button>
      </div>

      <div className="detail">
        <GroupBuyGallery images={detail.images} alt={detail.productName} />

        <div className="detail-info">
          <p className="status">
            {status.label} · {remaining(detail.recruitEndAt)}
          </p>
          <h1>{detail.productName}</h1>
          <p className="list-price">
            1인 기준 정상가 <s>{won(detail.listPrice)}</s>
          </p>

          <p className="group-price">
            1인 분담금 <b>{won(detail.unitShareAmount)}</b>
            {discount > 0 && <span className="off"> {discount}% 저렴</span>}
          </p>
          <p className="price-note">
            + 배송비 {won(detail.shippingFee)} · 총 <b>{won(totalAmount)}</b>
          </p>

          <p className="join">
            현재 {detail.paidCount}명 참여 / 목표 {detail.targetCount}명
          </p>
          <ProgressBar current={detail.paidCount} target={detail.targetCount} />

          <p className="note">
            목표 인원 달성 시 결제가 확정됩니다.
            <br />
            미달 시 결제는 자동 취소됩니다.
          </p>

          <div className="box rows">
            <DataRow
              label={
                <>
                  상품 분담금{' '}
                  <span className="small muted">
                    {number(detail.productPrice)}원 ÷ {detail.targetCount}명
                  </span>
                </>
              }
            >
              {won(detail.unitShareAmount)}
            </DataRow>
            <DataRow label="배송비">{won(detail.shippingFee)}</DataRow>
            <DataRow label="개인 결제금액">{won(totalAmount)}</DataRow>
          </div>

          <div className="box rows mt16">
            <DataRow label="모집 마감">{date(detail.recruitEndAt)}</DataRow>
            <DataRow label="판매자 정보">{detail.sellerName}</DataRow>
            <DataRow label="참여 취소">모집 종료 전까지 가능</DataRow>
            <DataRow
              label={
                <>
                  청약철회 <span className="small muted">전자상거래법</span>
                </>
              }
            >
              수령 다음 날부터 {POLICY_DEFAULTS.autoConfirmDays}일 이내
            </DataRow>
            <DataRow label="단순변심 반품비">{won(detail.shippingFee * 2)} · 구매자 부담</DataRow>
            {detail.withdrawalRestrictionReason !== null && (
              <DataRow label="청약철회 제한">{detail.withdrawalRestrictionReason}</DataRow>
            )}
          </div>

          <Button block className="mt24" onClick={join} disabled={!isJoinable}>
            {isJoinable ? '공동구매 참여하기' : '참여할 수 없는 공동구매입니다'}
          </Button>
        </div>
      </div>

      <section className="section" style={{ paddingTop: 0 }}>
        <div className="panel">
          <h3>상품 설명</h3>
          <p className="sub">{detail.sellerName} 제공</p>
          <p className="para">{detail.description}</p>
        </div>

        <div className="panel mt16">
          <h3>공동구매 진행 방식</h3>
          <p className="sub">공구한 에스크로 기준</p>
          <DataRow label="모집 기간">
            {date(detail.recruitStartAt)} ~ {date(detail.recruitEndAt)}
          </DataRow>
          <DataRow label="분담금 계산">
            {number(detail.productPrice)}원 ÷ {detail.targetCount}명
          </DataRow>
          <DataRow label="배송비 부담">참여자 개별 부담</DataRow>
          <DataRow label="플랫폼 수수료">
            판매자 부담 · 분담금의 {percent(POLICY_DEFAULTS.platformFeeRate)}
          </DataRow>
          <DataRow label="자동 구매확정">배송완료 후 {POLICY_DEFAULTS.autoConfirmDays}일</DataRow>
          <DataRow label="정산 시점">
            배송완료 후 {POLICY_DEFAULTS.settlementEligibleDays}일 경과 · 구매확정 조건
          </DataRow>
        </div>
      </section>
    </>
  )
}
