import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { adminApi } from '@/entities/admin/api'
import { asUtc, POLICY_GROUPS, type Policy } from '@/entities/admin/model'
import { adminQueries } from '@/entities/admin/queries'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { dateTime } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { Panel } from '@/shared/ui/Panel'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

/** 정책 키를 묶음별로 나눕니다. 묶음에 없는 키는 마지막 "기타" 로 모읍니다. */
function groupPolicies(policies: readonly Policy[]): { title: string; hint: string; rows: Policy[] }[] {
  const byKey = new Map(policies.map((policy) => [policy.policyKey, policy]))
  const groups = POLICY_GROUPS.map((group) => {
    const rows: Policy[] = []
    for (const key of group.keys) {
      const policy = byKey.get(key)
      if (policy !== undefined) {
        rows.push(policy)
        byKey.delete(key)
      }
    }
    return { title: group.title, hint: group.hint, rows }
  }).filter((group) => group.rows.length > 0)

  // 프론트가 모르는 새 키. 화면에서 사라지지 않게 그대로 보여줍니다.
  const rest = [...byKey.values()]
  if (rest.length > 0) {
    groups.push({
      title: '기타',
      hint: '프론트 분류에 없는 키입니다. 백엔드에 새로 추가됐을 수 있습니다.',
      rows: rest,
    })
  }
  return groups
}

/**
 * ADM-03·04 운영 정책.
 *
 * 값 하나를 바꾸면 진행 중인 거래에도 곧바로 반영됩니다. 예를 들어
 * `AUTO_CONFIRM_DAYS` 를 줄이면 이미 배송완료된 건의 자동 확정 시점이 당겨지고,
 * `PLATFORM_FEE_RATE` 를 올리면 아직 정산되지 않은 건의 수수료가 달라집니다.
 * 그래서 저장 전에 바뀌는 값을 한 번 더 보여주고, 변경은 감사 로그에 남습니다.
 *
 * 값 형식(숫자·비율·문자열)은 서버가 검증합니다. 프론트에서 따로 막지 않는
 * 이유는 키마다 형식이 다르고 그 규칙이 백엔드에만 있기 때문입니다 —
 * 여기서 흉내 내면 두 곳이 어긋납니다.
 */
export default function AdminPoliciesPage() {
  useDocumentTitle('운영 정책')

  const query = useQuery(adminQueries.policies())

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">운영 정책</h2>
          <p className="sec-sub">
            수수료·기한·외부 연동 설정입니다. 바꾸면 진행 중인 거래에도 바로 반영됩니다.
          </p>
        </div>
      </div>

      <QueryBoundary query={query} loadingLabel="정책값을 불러오는 중입니다.">
        {(policies) =>
          policies.length === 0 ? (
            <EmptyState>등록된 정책값이 없습니다.</EmptyState>
          ) : (
            <>
              {groupPolicies(policies).map((group, index) => (
                <Panel
                  key={group.title}
                  className={index > 0 ? 'mt16' : undefined}
                  title={group.title}
                  description={group.hint}
                >
                  {group.rows.map((policy) => (
                    <PolicyRow key={policy.policyKey} policy={policy} />
                  ))}
                </Panel>
              ))}
            </>
          )
        }
      </QueryBoundary>
    </section>
  )
}

function PolicyRow({ policy }: { policy: Policy }) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(policy.policyValue)

  const update = useMutation({
    mutationFn: () => adminApi.updatePolicy(policy.policyKey, value.trim()),
    onSuccess: async () => {
      setEditing(false)
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.all })
    },
  })

  const changed = value.trim() !== policy.policyValue

  const startEditing = () => {
    // 저장하지 않고 닫았다가 다시 열면 서버 값에서 시작해야 합니다.
    setValue(policy.policyValue)
    setEditing(true)
  }

  return (
    <div className="rowline" style={{ display: 'block' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16 }}>
        <span style={{ flex: 1 }}>
          <span className="mono">{policy.policyKey}</span>
          <span className="rowsub">
            {policy.description ?? '설명이 없는 키입니다.'}
            {policy.updatedAt !== null && (
              <>
                {' · '}
                {dateTime(asUtc(policy.updatedAt))} 변경
                {policy.updatedBy !== null && ` (관리자 #${String(policy.updatedBy)})`}
              </>
            )}
          </span>
        </span>

        {editing ? (
          <b style={{ flex: '0 0 300px', maxWidth: '100%' }}>
            <div className="input-action">
              <input
                className="input"
                value={value}
                aria-label={`${policy.policyKey} 값`}
                onChange={(event) => {
                  setValue(event.target.value)
                }}
              />
              <Button
                disabled={!changed || value.trim() === '' || update.isPending}
                onClick={() => {
                  update.mutate()
                }}
              >
                {update.isPending ? '저장 중…' : '저장'}
              </Button>
              <Button
                variant="ghost"
                onClick={() => {
                  setEditing(false)
                }}
              >
                취소
              </Button>
            </div>
          </b>
        ) : (
          <b style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className="mono">{policy.policyValue}</span>
            <span className="row-actions">
              <Button variant="ghost" onClick={startEditing}>
                변경
              </Button>
            </span>
          </b>
        )}
      </div>

      {editing && changed && (
        <p className="small muted mt8" style={{ marginBottom: 0 }}>
          <span className="mono">{policy.policyValue}</span> →{' '}
          <span className="mono">{value.trim()}</span> 으로 바뀝니다. 진행 중인 거래에도 즉시
          반영되고, 변경 기록이 감사 로그에 남습니다.
        </p>
      )}

      {update.isError && (
        <p className="ai-hint fail mt8" role="alert">
          {toMessage(update.error)}
        </p>
      )}
    </div>
  )
}
