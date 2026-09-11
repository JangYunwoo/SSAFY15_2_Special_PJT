import type { SyntheticEvent } from 'react'

/**
 * 폼 submit 핸들러의 이벤트 타입.
 *
 * React 의 `FormEvent` 는 실제로 존재하지 않는 이벤트라 @types/react 에서
 * deprecated 되었습니다. 대신 어떤 엘리먼트에서 어떤 네이티브 이벤트가
 * 올라온 것인지 명시합니다.
 */
export type SubmitHandlerEvent = SyntheticEvent<HTMLFormElement, SubmitEvent>
