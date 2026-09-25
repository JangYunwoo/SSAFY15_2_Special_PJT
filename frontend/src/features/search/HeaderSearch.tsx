import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'

/**
 * 헤더 전역 검색.
 *
 * 로고 바로 옆에 둡니다. 쇼핑몰인지 아닌지를 가장 빨리 알려주는 요소가 검색창인데,
 * 전에는 목록 화면까지 들어가야 검색할 수 있었습니다.
 *
 * 여기서는 입력만 받고 결과는 목록 화면이 그립니다. 헤더가 결과까지 그리면 어느
 * 화면에 있든 같은 목록을 두 벌 관리하게 됩니다. 목록 화면은 이미 검색어를 URL
 * 쿼리(keyword)로 읽으므로 그 주소로 보내기만 하면 됩니다.
 */
export function HeaderSearch() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const urlKeyword = params.get('keyword') ?? ''
  const [keyword, setKeyword] = useState(urlKeyword)
  const [focused, setFocused] = useState(false)
  const [syncedKeyword, setSyncedKeyword] = useState(urlKeyword)

  // 목록 화면의 검색어를 헤더가 그대로 들고 있어야 합니다. 검색해서 들어왔는데
  // 입력이 비어 있으면, 지금 무엇으로 찾은 목록인지 헤더가 부정하는 꼴이 됩니다.
  //
  // useEffect 가 아니라 렌더 중에 맞춥니다. 효과로 처리하면 한 번 옛 값으로 그린
  // 뒤 다시 그려서 글자가 깜빡입니다(React 의 "props 가 바뀔 때 state 조정" 패턴).
  if (urlKeyword !== syncedKeyword) {
    setSyncedKeyword(urlKeyword)
    setKeyword(urlKeyword)
  }

  return (
    <form
      className="gnb-search"
      role="search"
      onSubmit={(event) => {
        event.preventDefault()
        const trimmed = keyword.trim()
        void navigate(
          trimmed === '' ? '/group-buys' : `/group-buys?keyword=${encodeURIComponent(trimmed)}`,
        )
      }}
    >
      {/*
        누르면 예시 문구를 지웁니다. 입력하려고 눌렀는데 회색 글자가 남아 있으면
        커서 옆에서 계속 읽혀 방해가 됩니다. 비워두면 입력에만 집중됩니다.
      */}
      <input
        type="search"
        aria-label="상품 검색"
        placeholder={focused ? '' : '어떤 공동구매를 찾으세요?'}
        onFocus={() => {
          setFocused(true)
        }}
        onBlur={() => {
          setFocused(false)
        }}
        value={keyword}
        onChange={(event) => {
          setKeyword(event.target.value)
        }}
      />
      <button type="submit" aria-label="검색">
        {/* 돋보기. 아이콘 자산이 없어 인라인으로 그립니다 */}
        <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
          <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" strokeWidth="2" />
          <line
            x1="15.8"
            y1="15.8"
            x2="20"
            y2="20"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          />
        </svg>
      </button>
    </form>
  )
}
