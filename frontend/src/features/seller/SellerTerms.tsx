import { useEffect, useRef, useState, type ReactNode } from 'react'

interface TermsSection {
  id: string
  title: string
  basis: string
  body: ReactNode
}

/**
 * 판매자 전환 3단계에서 읽고 동의하는 소비자 보호 안내.
 *
 * 법 조문을 옮긴 것이 아니라 판매자가 지켜야 할 의무를 요약한 안내문입니다.
 * 정확한 요건은 각 법령 원문이 기준입니다.
 */
const SELLER_TERMS: readonly TermsSection[] = [
  {
    id: 'ecommerce',
    title: '통신판매업자의 표시·고지 의무',
    basis: '전자상거래법 제10조 · 제13조 · 제21조',
    body: (
      <>
        <h4>1. 판매자 신원 정보</h4>
        <p>
          판매자는 상호, 대표자 성명, 연락처, 사업장 주소 등 소비자가 판매자를 확인할 수 있는 정보를
          정확하게 제공해야 합니다. 등록한 정보가 바뀌면 지체 없이 판매자 정보를 수정해야 합니다.
        </p>
        <h4>2. 거래 조건의 고지</h4>
        <p>
          상품의 명칭·종류·내용, 가격과 그 밖의 비용, 공급 방법과 시기, 청약철회의 기한과 방법,
          교환·반품·환불 조건을 소비자가 계약 전에 알 수 있도록 상품 정보에 적어야 합니다.
        </p>
        <h4>3. 거짓·과장 표시의 금지</h4>
        <p>
          사실과 다르거나 부풀린 사실을 알리는 방법, 기만적인 방법으로 소비자를 유인하거나 거래하면
          안 됩니다. 이미지·원산지·성분·인증 여부 등 상품 정보는 실제와 같아야 합니다.
        </p>
        <h4>4. 공동구매 특칙</h4>
        <p>
          공구한의 공동구매는 모집 인원이 채워져야 성사됩니다. 모집 기간, 목표 인원, 1인 분담금을
          정확히 표시해야 하며, 모집 중에 소비자에게 불리하게 조건을 바꿀 수 없습니다.
        </p>
      </>
    ),
  },
  {
    id: 'withdrawal',
    title: '청약철회와 환불',
    basis: '전자상거래법 제17조 · 제18조',
    body: (
      <>
        <h4>1. 청약철회 기간</h4>
        <p>
          소비자는 상품을 받은 날부터 7일 이내에 청약을 철회할 수 있습니다. 상품이 표시·광고 내용과
          다르거나 계약과 다르게 이행된 경우에는 받은 날부터 3개월 이내, 그 사실을 안 날부터 30일
          이내에 철회할 수 있습니다.
        </p>
        <h4>2. 철회가 제한되는 경우</h4>
        <p>
          소비자의 책임으로 상품이 멸실·훼손된 경우, 사용하거나 일부 소비해 가치가 현저히 줄어든
          경우, 복제가 가능한 상품의 포장을 훼손한 경우 등에는 철회가 제한될 수 있습니다. 이 제한은
          상품 정보에 미리 분명하게 알린 경우에만 적용됩니다.
        </p>
        <h4>3. 환급 기한과 비용</h4>
        <p>
          상품을 돌려받은 날부터 3영업일 이내에 대금을 환급해야 하며, 늦어지면 지연 기간에 대한
          지연배상금이 붙습니다. 단순 변심에 따른 반환 비용은 소비자가 부담하지만, 상품이 표시와
          다르거나 하자가 있으면 판매자가 부담합니다.
        </p>
        <h4>4. 에스크로와 모집 실패</h4>
        <p>
          공구한의 결제 대금은 에스크로로 보관되며, 모집 인원이 채워지지 않으면 참여자 전원에게 전액
          환불됩니다. 판매자는 이 환불을 거부하거나 늦출 수 없습니다.
        </p>
      </>
    ),
  },
  {
    id: 'dispute',
    title: '분쟁 해결과 개인정보 보호',
    basis: '소비자기본법 · 개인정보 보호법 · 공구한 운영정책',
    body: (
      <>
        <h4>1. 소비자 분쟁 해결</h4>
        <p>
          상품에 문제가 있으면 공정거래위원회가 고시한 소비자분쟁해결기준에 따라 교환·환불·배상을
          해야 합니다. 소비자 불만과 분쟁을 처리하기 위한 연락 창구를 유지하고, 공구한과
          한국소비자원 등의 분쟁 조정 요청에 성실히 협조해야 합니다.
        </p>
        <h4>2. 구매자 개인정보</h4>
        <p>
          주문 처리로 알게 된 구매자의 이름·연락처·주소는 배송과 교환·반품 처리에만 쓸 수 있습니다.
          광고나 다른 판매에 쓰거나 제3자에게 제공하면 안 되며, 목적을 이룬 뒤에는 지체 없이
          파기해야 합니다.
        </p>
        <h4>3. 운영정책 위반 제재</h4>
        <p>
          발송 지연, 허위 정보 등록, 정당한 환불 거부 등 정책을 위반하면 공구한은 경고나 판매 정지로
          제재할 수 있으며, 위반이 쌓이면 제재가 무거워집니다. 제재 내용은 알림으로 전달됩니다.
        </p>
        <h4>4. 정산</h4>
        <p>
          판매 대금은 구매확정 이후 수수료를 뺀 금액이 인증한 정산계좌로 지급됩니다. 반품·환불이
          진행 중인 주문은 처리가 끝날 때까지 정산이 보류될 수 있습니다.
        </p>
      </>
    ),
  },
]

/**
 * 스크롤을 끝까지 내려야 체크할 수 있는 동의 항목 하나.
 *
 * 글이 상자보다 짧으면 처음부터 끝까지 보인 것이므로 바로 체크할 수 있게 합니다.
 */
function TermsBox({
  section,
  checked,
  onCheckedChange,
}: {
  section: TermsSection
  checked: boolean
  onCheckedChange: (checked: boolean) => void
}) {
  const boxRef = useRef<HTMLDivElement>(null)
  const [readToEnd, setReadToEnd] = useState(false)
  /** 끝까지 읽기 전에 체크하려 한 적이 있는지. 그때만 안내를 보입니다. */
  const [nudged, setNudged] = useState(false)

  const measure = () => {
    const box = boxRef.current
    if (box === null) return
    // 소수점 스크롤 값 때문에 딱 맞아떨어지지 않는 경우가 있어 2px 여유를 둡니다.
    if (box.scrollTop + box.clientHeight >= box.scrollHeight - 2) setReadToEnd(true)
  }

  useEffect(measure, [])

  const labelId = `terms-${section.id}-title`
  const hintId = `terms-${section.id}-hint`

  return (
    <div className="terms">
      <div className="terms-head">
        <b id={labelId}>{section.title}</b>
        <span>{section.basis}</span>
      </div>
      <div
        ref={boxRef}
        className="terms-body"
        tabIndex={0}
        role="region"
        aria-labelledby={labelId}
        onScroll={measure}
      >
        {section.body}
      </div>
      {/*
        disabled 로 막지 않습니다. 커서와 클릭이 통째로 죽으면 왜 안 눌리는지 알 길이
        없습니다. 대신 끝까지 읽기 전에 누르면 체크는 그대로 두고 안내를 띄웁니다.
      */}
      <label className="terms-agree">
        <input
          type="checkbox"
          checked={checked}
          aria-describedby={nudged && !readToEnd ? hintId : undefined}
          onChange={(event) => {
            if (!readToEnd) {
              setNudged(true)
              return
            }
            onCheckedChange(event.target.checked)
          }}
        />
        <span>위 내용을 확인하였으며, 이에 동의합니다.</span>
        {nudged && !readToEnd && (
          <em id={hintId} role="alert">
            내용을 끝까지 읽어주세요
          </em>
        )}
      </label>
    </div>
  )
}

/** 세 항목을 모두 체크했는지를 부모에게 알립니다. */
export function SellerTerms({ onAllAgreedChange }: { onAllAgreedChange: (all: boolean) => void }) {
  const [agreed, setAgreed] = useState<Record<string, boolean>>({})

  useEffect(() => {
    onAllAgreedChange(SELLER_TERMS.every((section) => agreed[section.id] === true))
  }, [agreed, onAllAgreedChange])

  return (
    <>
      {SELLER_TERMS.map((section) => (
        <TermsBox
          key={section.id}
          section={section}
          checked={agreed[section.id] === true}
          onCheckedChange={(checked) => {
            setAgreed((prev) => ({ ...prev, [section.id]: checked }))
          }}
        />
      ))}
    </>
  )
}
