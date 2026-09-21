export interface PostcodeResult {
  zonecode: string
  address: string
  roadAddress: string
  jibunAddress: string
  userSelectedType: 'R' | 'J'
}

export interface SearchAddress { zipcode: string; address1: string }

export function selectedAddress(data: PostcodeResult): SearchAddress {
  const address1 = (data.userSelectedType === 'R' ? data.roadAddress : data.jibunAddress) || data.address
  if (!/^\d{5}$/.test(data.zonecode) || !address1.trim()) {
    throw new Error('주소 검색 결과가 올바르지 않습니다. 다시 선택해 주세요.')
  }
  return { zipcode: data.zonecode, address1: address1.trim() }
}

type PostcodeConstructor = new (options: {
    oncomplete: (result: PostcodeResult) => void
    width: string
    height: string
  }) => { embed: (container: HTMLElement) => void }

declare global {
  interface Window {
    kakao?: { Postcode?: PostcodeConstructor }
    daum?: { Postcode?: PostcodeConstructor }
  }
}

let loading: Promise<PostcodeConstructor> | null = null
const available = (): PostcodeConstructor | undefined => window.kakao?.Postcode ?? window.daum?.Postcode

/** 공식 SDK를 요청한 시점에만 한 번 로드하고 실패하면 다음 요청에서 재시도한다. */
export function loadPostcode(): Promise<PostcodeConstructor> {
  const constructor = available()
  if (constructor) return Promise.resolve(constructor)
  if (loading) return loading
  loading = new Promise<PostcodeConstructor>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'
    script.async = true
    const fail = () => {
      window.clearTimeout(timer)
      script.remove()
      reject(new Error('주소 검색을 불러오지 못했습니다. 인터넷 연결을 확인하고 다시 시도해 주세요.'))
    }
    const timer = window.setTimeout(fail, 15_000)
    script.onerror = fail
    script.onload = () => {
      window.clearTimeout(timer)
      const loaded = available()
      if (loaded) resolve(loaded)
      else fail()
    }
    document.head.append(script)
  }).catch((error: unknown) => {
    loading = null
    throw error
  })
  return loading
}
