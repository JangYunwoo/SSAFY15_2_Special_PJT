import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import { GROUP_BUY_STATUS_DISPLAY } from '@/entities/model'
import { displayFor } from '@/entities/statusDisplay'
import type { GroupBuyDetail } from '@/entities/groupBuy/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { useIsSignedIn } from '@/features/auth/sessionStore'
import { useIsLiked, useToggleLike } from '@/features/like/likeStore'
import { GroupBuyGallery } from '@/features/groupBuy/GroupBuyGallery'
import { recordRecommendationEvents } from '@/features/recommendation/tracking'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { date, number, percent, remaining, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { cx } from '@/shared/lib/cx'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { HeartIcon } from '@/shared/ui/icons'
import { ProgressBar } from '@/shared/ui/ProgressBar'
import { QueryBoundary } from '@/shared/ui/states'


/**
 * 상세의 찜하기.
 *
 * 카드의 하트(features/groupBuy/GroupBuyCard)와 같은 저장소를 씁니다. 목록에서
 * 찜한 건은 여기서도 채워져 있어야 하고, 반대도 마찬가지입니다.
 *
 * 로그인하지 않았으면 로그인 화면으로 보냅니다. 버튼을 감추지 않는 이유는 이런
 * 기능이 있다는 것 자체가 로그인할 이유가 되기 때문입니다.
 *
 * 글자는 넣지 않습니다. "찜하기" 와 "찜함" 은 길이가 달라 누를 때마다 버튼 폭이
 * 바뀌고, 그만큼 옆의 참여 버튼도 밀립니다. 하트는 채워짐만으로 상태가 읽히므로
 * 이름은 aria-label 로만 남깁니다.
 */
function LikeButton({ id, name }: { id: string; name: string }) {
  const navigate = useNavigate()
  const liked = useIsLiked(id)
  const toggleLike = useToggleLike()

  return (
    <button
      type="button"
      className={cx('btn-ghost detail-like', liked && 'on')}
      aria-pressed={liked}
      aria-label={`${name} ${liked ? '찜 해제' : '찜하기'}`}
      onClick={() => {
        if (!toggleLike(id)) {
          void navigate('/login', { state: { from: `/group-buys/${id}` } })
        }
      }}
    >
      <HeartIcon filled={liked} />
    </button>
  )
}

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

  const status = displayFor(GROUP_BUY_STATUS_DISPLAY, detail.status, '공동구매 상태')
  // 서버가 확정한 1인 결제금액을 그대로 씁니다 (GRP-02 personalPaymentAmount).
  const totalAmount = detail.personalPaymentAmount
  const isJoinable = detail.status === 'RECRUITING' && detail.paidCount < detail.targetCount

  useEffect(() => {
    if (!isSignedIn) return
    void recordRecommendationEvents([
      {
        groupBuyId: detail.id,
        eventType: 'DETAIL_VIEW',
        source: 'GROUP_BUY_DETAIL',
      },
    ])
  }, [detail.id, isSignedIn])

  const join = () => {
    if (!isSignedIn) {
      void navigate('/login', { state: { from: `/group-buys/${detail.id}` } })
      return
    }
    void navigate(`/checkout/${detail.id}/shipping`)
  }

  return (
    <>
      <div className="detail">
        <GroupBuyGallery images={detail.images} alt={detail.productName} />

        <div className="detail-info">
          <p className="status">
            {status.label} · {remaining(detail.recruitEndAt)}
          </p>
          <h1>{detail.productName}</h1>

          {/*
            정상가와 할인율은 걷어냈습니다. 백엔드가 listPrice 를 products.price
            로 채우는데 그 값은 "공동구매 1회분 총액"이라, 1인 분담금을 목표
            인원 전체의 금액과 비교하게 됩니다(18,000원 대 720원 = 96% 저렴).
            목표 인원이 늘수록 할인율이 올라가는 수치였습니다.
          */}
          <p className="group-price">
            1인 분담금 <b>{won(detail.unitShareAmount)}</b>
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

          {/*
            TODO(임시): 배송 안내. 무료배송 기준과 도서산간 추가금은 아직 상품에
            없는 값이라 문구를 박아 두었습니다. 백엔드에 필드가 생기면 바꿉니다.
            배송비만 실제 값(shippingFee)입니다.
          */}
          <dl className="detail-ship">
            <dt>배송정보</dt>
            <dd>3일 이내 배송 시작</dd>
            <dt>배송비</dt>
            <dd>
              <b>{won(detail.shippingFee)}</b>
              <span>70,000원 이상 구매시 무료배송</span>
              <span>제주/도서산간 6,000원 추가</span>
            </dd>
          </dl>

          {/* 사진 아래 선과 버튼 아래 선을 맞춥니다 (.detail-actions 의 margin-top:auto) */}
          <div className="detail-actions">
            <LikeButton id={detail.id} name={detail.productName} />
            <Button className="detail-join" onClick={join} disabled={!isJoinable}>
              {isJoinable ? '공동구매 참여하기' : '참여할 수 없습니다'}
            </Button>
          </div>
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
          {/*
            금액 산정 근거는 여기에 둡니다. 위쪽 사이드바에 표로 세워 두었더니
            "1인 분담금 / 배송비 / 총액" 이라는 같은 말을 바로 위 큰 글씨와 두 번
            하게 되고, 참여 버튼까지 가는 길에 표가 하나 끼어 있었습니다.
            계산식은 결정을 돕는 정보가 아니라 결정한 뒤 확인하는 조건에 가깝습니다.
          */}
          <DataRow label="모집 마감">{date(detail.recruitEndAt)}</DataRow>
          <DataRow label="판매자">{detail.sellerName}</DataRow>
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
          <DataRow label="분담금 산정">
            상품가 {number(detail.productPrice)}원 ÷ 목표 {detail.targetCount}명 ={' '}
            {won(detail.unitShareAmount)} <span className="small muted">원 단위 올림</span>
          </DataRow>
          <DataRow label="배송비 부담">1인 {won(detail.shippingFee)} · 참여자 개별 부담</DataRow>
          <DataRow label="개인 결제금액">
            {won(totalAmount)} <span className="small muted">분담금 + 배송비</span>
          </DataRow>
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


