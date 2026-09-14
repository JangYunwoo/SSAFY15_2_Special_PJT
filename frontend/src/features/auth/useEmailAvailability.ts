import { useMutation } from '@tanstack/react-query'
import { useCallback, useState } from 'react'

import { authApi } from '@/entities/auth/api'
import { toMessage } from '@/shared/api/errors'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'

import {
  AVAILABLE,
  CHECKING,
  DUPLICATE,
  UNSUPPORTED,
  failed,
  normalizeEmail,
  outcomeFor,
  validateEmail,
  type EmailCheckOutcome,
  type EmailCheckRecord,
} from './emailCheck'

export interface EmailAvailabilityCheck {
  /** 중복확인 버튼이 부르는 함수 */
  check: (email: string) => void
  /** 지금 입력값 기준의 확인 결과. 이메일을 고치면 자동으로 idle 로 돌아갑니다. */
  outcomeOf: (email: string) => EmailCheckOutcome
}

/**
 * 회원가입 이메일 중복확인.
 *
 * 확인 결과를 "확인한 이메일"과 함께 들고 있다가, 입력이 달라지면 버립니다.
 * 통과시킨 주소와 실제로 가입되는 주소가 어긋나는 것을 막기 위해서입니다.
 *
 * 형식 오류는 요청을 보내지 않고 바로 돌려주고, 백엔드에 확인 API 가 없으면
 * (`BACKEND_FEATURES.emailAvailability`) 404 오류 대신 안내로 대체합니다.
 */
export function useEmailAvailability(): EmailAvailabilityCheck {
  const [record, setRecord] = useState<EmailCheckRecord | null>(null)

  const mutation = useMutation({
    mutationFn: (email: string) => authApi.checkEmailAvailability(email),
    onSuccess: (result, email) => {
      setRecord({
        email: normalizeEmail(email),
        outcome: result.available ? AVAILABLE : DUPLICATE,
      })
    },
    onError: (error, email) => {
      setRecord({ email: normalizeEmail(email), outcome: failed(toMessage(error)) })
    },
  })

  const { mutate } = mutation

  const check = useCallback(
    (rawEmail: string): void => {
      const email = rawEmail.trim()
      const invalid = validateEmail(email)
      if (invalid) {
        setRecord({ email: normalizeEmail(email), outcome: invalid })
        return
      }
      if (!BACKEND_FEATURES.emailAvailability) {
        setRecord({ email: normalizeEmail(email), outcome: UNSUPPORTED })
        return
      }
      mutate(email)
    },
    [mutate],
  )

  const outcomeOf = useCallback(
    (email: string): EmailCheckOutcome => {
      // 확인 중인 주소를 그대로 들고 있을 때만 진행 상태를 보여줍니다.
      if (mutation.isPending && normalizeEmail(mutation.variables) === normalizeEmail(email)) {
        return CHECKING
      }
      return outcomeFor(record, email)
    },
    [mutation.isPending, mutation.variables, record],
  )

  return { check, outcomeOf }
}
