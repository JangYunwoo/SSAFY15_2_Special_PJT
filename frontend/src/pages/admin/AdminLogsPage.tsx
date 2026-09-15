import { useState } from 'react'

import { useQuery } from '@tanstack/react-query'

import { asUtc, type AuditLog, type ExternalApiLog } from '@/entities/admin/model'
import { adminQueries } from '@/entities/admin/queries'
import { dateTime } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { usePageParam } from '@/shared/lib/hooks/usePageParam'
import { ChipFilter } from '@/shared/ui/Chips'
import { Pagination } from '@/shared/ui/Pagination'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

type Tab = 'external' | 'audit'

const TABS: { value: Tab; label: string }[] = [
  { value: 'external', label: '외부 금융 API' },
  { value: 'audit', label: '관리자 감사 로그' },
]

/** SSAFY 금융망은 `H0000` 이 성공입니다. 나머지는 전부 실패로 봅니다. */
const SSAFY_SUCCESS_CODE = 'H0000'

/**
 * ADM-05·07 운영 로그.
 *
 * 두 로그를 한 화면에 두었습니다. 장애를 볼 때 묻는 순서가 "외부 호출이
 * 실패했나 → 그때 누가 무엇을 바꿨나" 라서, 화면을 옮겨 다니지 않아도
 * 되게 탭으로 붙였습니다.
 *
 * 필터는 없습니다. 백엔드가 페이지 말고는 조건을 받지 않아서인데
 * (`findAll(pageable)`), 조건 조회가 필요해지면 백엔드부터 열어야 합니다.
 */
export default function AdminLogsPage() {
  useDocumentTitle('운영 로그')

  const { page, goToPage } = usePageParam()
  const [tab, setTab] = useState<Tab>('external')

  const changeTab = (next: Tab) => {
    setTab(next)
    // 로그 종류가 바뀌면 쪽 번호는 의미가 없습니다.
    goToPage(0)
  }

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">운영 로그</h2>
          <p className="sec-sub">
            외부 금융망 호출 결과와 관리자 조치 이력입니다. 최신순으로 쌓입니다.
          </p>
        </div>
      </div>

      <ChipFilter label="로그 종류" value={tab} onChange={changeTab} options={TABS} />

      {tab === 'external' ? (
        <ExternalApiLogs page={page} onPageChange={goToPage} />
      ) : (
        <AuditLogs page={page} onPageChange={goToPage} />
      )}
    </section>
  )
}

function ExternalApiLogs({
  page,
  onPageChange,
}: {
  page: number
  onPageChange: (index: number) => void
}) {
  const query = useQuery(adminQueries.externalApiLogs({ page, size: 20 }))

  return (
    <Panel
      title="외부 금융 API 호출 이력"
      description="SSAFY 금융망 요청과 응답 코드 · 응답코드 H0000 이 성공입니다"
    >
      <QueryBoundary query={query} loadingLabel="호출 이력을 불러오는 중입니다.">
        {(result) =>
          result.items.length === 0 ? (
            <EmptyState>기록된 호출 이력이 없습니다.</EmptyState>
          ) : (
            <>
              <div className="table-scroll">
                <table className="list">
                  <thead>
                    <tr>
                      <th>호출 시각</th>
                      <th>API</th>
                      <th>대상</th>
                      <th>거래번호</th>
                      <th>결과</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.items.map((log) => (
                      <ExternalApiLogRow key={log.logId} log={log} />
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination meta={result.meta} onChange={onPageChange} label="호출 이력 페이지" />
            </>
          )
        }
      </QueryBoundary>
    </Panel>
  )
}

function ExternalApiLogRow({ log }: { log: ExternalApiLog }) {
  const succeeded = log.responseCode === SSAFY_SUCCESS_CODE

  return (
    <tr>
      <td>{dateTime(asUtc(log.calledAt))}</td>
      <td>
        <span className="mono">{log.apiServiceCode}</span>
      </td>
      <td>
        {log.targetType ?? '-'}
        {log.targetId !== null && (
          <>
            <br />
            <span className="small muted mono">#{log.targetId}</span>
          </>
        )}
      </td>
      <td>
        <span className="mono small">{log.institutionTxNo ?? '-'}</span>
      </td>
      <td>
        <StateChip tone={succeeded ? 'ok' : 'alert'}>{log.responseCode ?? '응답 없음'}</StateChip>
        {log.responseMessage !== null && (
          <>
            <br />
            <span className="small muted">{log.responseMessage}</span>
          </>
        )}
      </td>
    </tr>
  )
}

function AuditLogs({
  page,
  onPageChange,
}: {
  page: number
  onPageChange: (index: number) => void
}) {
  const query = useQuery(adminQueries.auditLogs({ page, size: 20 }))

  return (
    <Panel
      title="관리자 감사 로그"
      description="정책 변경 등 관리자가 한 조치의 변경 전·후 값"
    >
      <QueryBoundary query={query} loadingLabel="감사 로그를 불러오는 중입니다.">
        {(result) =>
          result.items.length === 0 ? (
            <EmptyState>기록된 조치가 없습니다.</EmptyState>
          ) : (
            <>
              <div className="table-scroll">
                <table className="list fixed">
                  <colgroup>
                    <col style={{ width: '18%' }} />
                    <col style={{ width: '14%' }} />
                    <col style={{ width: '18%' }} />
                    <col style={{ width: '25%' }} />
                    <col style={{ width: '25%' }} />
                  </colgroup>
                  <thead>
                    <tr>
                      <th>시각</th>
                      <th>수행자</th>
                      <th>조치</th>
                      <th>변경 전</th>
                      <th>변경 후</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.items.map((log) => (
                      <AuditLogRow key={log.logId} log={log} />
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination meta={result.meta} onChange={onPageChange} label="감사 로그 페이지" />
            </>
          )
        }
      </QueryBoundary>
    </Panel>
  )
}

function AuditLogRow({ log }: { log: AuditLog }) {
  return (
    <tr>
      <td>{dateTime(asUtc(log.createdAt))}</td>
      <td>
        {log.actorType}
        {log.actorId !== null && (
          <>
            <br />
            <span className="small muted mono">#{log.actorId}</span>
          </>
        )}
      </td>
      <td>
        <b>{log.action}</b>
        <br />
        <span className="small muted">
          {log.targetType ?? '-'}
          {log.targetId !== null && ` #${String(log.targetId)}`}
        </span>
      </td>
      <td>{log.beforeJson === null ? '-' : <pre className="json">{log.beforeJson}</pre>}</td>
      <td>{log.afterJson === null ? '-' : <pre className="json">{log.afterJson}</pre>}</td>
    </tr>
  )
}
