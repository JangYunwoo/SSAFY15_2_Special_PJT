import { lazy, type ComponentType, type LazyExoticComponent } from 'react'

/**
 * 배포로 청크 파일명이 바뀌었을 때 브라우저가 옛 이름을 받으러 가서 나는 오류.
 *
 * 브라우저마다 문구가 다릅니다. 코드로는 구분되지 않아 메시지로 봅니다.
 */
const CHUNK_ERROR_PATTERNS = [
  'failed to fetch dynamically imported module', // Chrome, Edge
  'error loading dynamically imported module', // Firefox
  'importing a module script failed', // Safari
]

export function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error)
  const lower = message.toLowerCase()
  return CHUNK_ERROR_PATTERNS.some((pattern) => lower.includes(pattern))
}

const RELOAD_KEY = 'gongguhan.chunkReloadAt'
/** 이 시간 안에 또 실패하면 새로고침으로 풀리는 문제가 아니라고 봅니다. */
const RELOAD_COOLDOWN_MS = 10_000

/**
 * 새로고침으로 풀어볼 오류인가.
 *
 * 청크 오류가 아니면 건드리지 않습니다. 청크 오류라도 방금 새로고침했다면 다시
 * 하지 않습니다 - 서버에 파일이 정말 없는 경우 무한 새로고침에 빠지고, 사용자는
 * 화면이 계속 깜빡이는 것 말고는 아무것도 할 수 없게 됩니다. 그때는 차라리
 * 오류 화면을 보여주는 편이 낫습니다.
 */
export function shouldReloadForChunkError(
  error: unknown,
  now: number,
  lastReloadAt: number | null,
): boolean {
  if (!isChunkLoadError(error)) return false
  if (lastReloadAt === null) return true
  return now - lastReloadAt > RELOAD_COOLDOWN_MS
}

/** sessionStorage 는 사생활 보호 모드 등에서 던질 수 있어 전부 감쌉니다. */
function readReloadMark(): number | null {
  try {
    const raw = sessionStorage.getItem(RELOAD_KEY)
    if (raw === null) return null
    const parsed = Number(raw)
    return Number.isFinite(parsed) ? parsed : null
  } catch {
    return null
  }
}

function writeReloadMark(now: number): void {
  try {
    sessionStorage.setItem(RELOAD_KEY, String(now))
  } catch {
    // 표시를 못 남기면 쿨다운이 동작하지 않지만, 새로고침 자체는 해 볼 가치가 있습니다.
  }
}

function clearReloadMark(): void {
  try {
    sessionStorage.removeItem(RELOAD_KEY)
  } catch {
    // 무시합니다.
  }
}

/**
 * React 의 lazy 와 같은 제약입니다. props 가 있는 화면(KakaoPayCallbackPage)과
 * 없는 화면을 모두 받아야 해서 좁힐 수 없습니다.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any -- React.lazy 의 시그니처를 그대로 따릅니다
type RouteComponent = ComponentType<any>

/**
 * 라우트용 lazy. 배포 직후 옛 청크를 받으러 가 실패하면 한 번 새로고침합니다.
 *
 * <p>탭을 열어 둔 채로 배포가 일어나면, 메모리에 있는 옛 index.html 이 사라진
 * 파일명을 가리킵니다. 지연 로딩하는 화면으로 이동하는 순간 404 가 나고 사용자는
 * 영문 오류 화면을 봅니다. index.html 은 캐시하지 않으므로 새로고침 한 번이면
 * 새 파일명을 받아옵니다.
 */
export function lazyRoute<T extends RouteComponent>(
  factory: () => Promise<{ default: T }>,
): LazyExoticComponent<T> {
  return lazy(async () => {
    try {
      const module = await factory()
      // 성공했으면 표시를 지웁니다. 남겨 두면 한참 뒤의 다음 배포 때 쿨다운에 걸립니다.
      clearReloadMark()
      return module
    } catch (error) {
      if (shouldReloadForChunkError(error, Date.now(), readReloadMark())) {
        writeReloadMark(Date.now())
        window.location.reload()
        // reload 는 즉시 멈추지 않습니다. 그대로 두면 오류 화면이 한 번 깜빡입니다.
        await new Promise<never>(() => {
          /* 영원히 대기 */
        })
      }
      throw error
    }
  })
}
