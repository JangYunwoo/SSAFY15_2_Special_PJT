# EC2 DevelopV2 배포 및 실제 MySQL 사전 검증 — 2026-09-22

관련 Jira: S15P21B205-220. 사용자가 EC2의 DevelopV1 실행본을 DevelopV2로 교체하도록 명시적으로 승인했다. 이번 작업은 배포와 배포 확인이며, 인수인계의 잔여 제품 결함을 수정하는 작업을 재개한 것은 아니다.

## 1. 실제 결과와 범위

- 서비스: https://j15b205.p.ssafy.io/
- 서버 저장소: `/home/ubuntu/S15P21B205`, 현재 브랜치 DevelopV2.
- 이전 소스: `3384b9e95737dd0e5484fa928383b1c8b022582d` (DevelopV1).
- 새 실행 이미지의 소스: `5e6c9b874e7bcb0632b04994b5c9e775fc576169` (DevelopV2).
- 배포 후 이 기록만 추가하는 문서 MR은 실행 이미지 소스를 바꾸지 않는다.
- 백엔드·AI·프런트(Nginx) 이미지를 EC2에서 새로 빌드해 교체했다. MySQL·Redis·인증서 컨테이너와 기존 볼륨은 교체·삭제하지 않았다.
- 중지 시작 기록: 2026-09-22 10:27:21 KST. 외부 응답 확인 기록: 10:28:19 KST. 약 58초는 중지부터 확인까지의 관측 구간이며 정확한 장애 지속시간이나 성능 측정값은 아니다.
- 백엔드·AI·MySQL·Redis healthy, HTTPS 메인 페이지 200, 백엔드 health UP 확인.
- DevelopV1 원격 브랜치를 수정하거나 DevelopV2를 DevelopV1에 병합하지 않았다.

## 2. 왜 단순 pull로 처리하지 않았는가

서버는 DevelopV1을 체크아웃하고 있었다. 여기서 pull만 하면 DevelopV1 갱신이며, pull origin DevelopV2는 현재 브랜치로 병합을 시도하는 다른 동작이다. 브랜치를 명시적으로 전환했다.

실제 EC2에는 GitLab HTTPS 인증이 없어서 비대화식 fetch가 `could not read Username`으로 실패했다. 토큰을 서버에 복사하거나 원격 URL에 넣지 않았다. 인증된 로컬에서 origin/DevelopV2를 fetch하여 SHA를 확인하고, 기존 3384b9e를 전제로 하는 증분 Git bundle을 생성·검증해 SSH로 전송했다. EC2에서도 bundle을 검증한 뒤 origin/DevelopV2 참조로 가져왔다. 단순 파일 복사가 아니라 커밋 이력이 보존되는 전달이다.

기존 remote fetch 설정은 단일 브랜치용이어서 tracking branch 생성도 처음에 거절됐다. DevelopV2 fetch refspec을 추가하고 upstream을 origin/DevelopV2로 설정했다. **인증 문제 자체는 해결한 것이 아니다. 다음 원격 pull에는 기존 정책에 맞는 GitLab 인증 준비 또는 다시 검증한 bundle 전달이 필요하다.**

서버의 `deploy/env/backend.env.example`에는 기존 미커밋 변경이 있었다. 원격 V1→V2에서 그 경로가 달라지지 않는 것을 확인했고, 덮어쓰기·stash·reset 없이 보존했다. 실제 env와 GCP 인증 파일도 그대로 사용했다.

## 3. 백업과 복구 자산

서버 전용 백업 폴더: `/home/ubuntu/deploy-backup-20260922-iNEKW5`.

생성 시 umask 077을 적용했다. 환경/DB 백업에는 비밀값과 개인정보가 포함될 수 있으므로 Git, Jira, 공개 다운로드, 응답에 파일 내용을 올리지 않는다.

| 파일 또는 자산 | 목적 |
| --- | --- |
| `previous-commit.txt` | 배포 이전 커밋 |
| `server-local-changes.patch` | 서버 기존 미커밋 설정 변경 보존 |
| `deploy-config.tar.gz` | 기존 deploy 설정·환경·시크릿 복구용 |
| `previous-images.txt` | 기존 실행 이미지 ID |
| `preflight-db.sql.gz` | 실행 중 일관된 snapshot으로 받은 사전 검증용 dump |
| `final-before-cutover.sql.gz` | 요청과 백엔드를 정지한 뒤 받은 최종 배포 직전 dump |
| `before-counts.tsv` | 주요 테이블 배포 직전 건수 |
| `build.log` | EC2 이미지 빌드 결과 |
| `cutover-start.txt`, `cutover-end.txt` | 전환 관측 시각 |
| `backend-health.json` | 새 백엔드 내부 상태 확인 |

dump는 `--single-transaction --routines --triggers --events --set-gtid-purged=OFF --no-tablespaces`로 생성했고 gzip 무결성을 확인했다. 최종 dump SHA256은 `1399b593f6d5c2072ca80140a3b2f9e0c9d99eb7bee1a41de2ae4dff82a73201`이다.

기존 backend/nginx/ai 이미지는 각각 `gongguhan-prod-<service>:deploy-backup-20260922-iNEKW5` 태그로 보존했다. 새 이미지를 빌드해도 이전 이미지가 덮여 사라지지 않도록 한 것이다. 이 백업은 같은 EC2 안에 있어 서버 전체 장애를 대비한 외부 백업까지 제공하는 것은 아니다.

## 4. 실제 MySQL 복사본 검증을 먼저 한 이유

기존 로컬 전체 시험은 H2 중심이었고, 서버는 예상한 V16이 아니라 V15까지였다. 따라서 V16~V22 총 7개를 적용해야 했다. V22가 제거하는 `fk_payments_address`의 실제 존재도 먼저 확인했다. 과거 보고서의 스키마 버전을 그대로 믿고 실행하지 않았다.

외부 연결 없는 Docker internal 네트워크 `gongguhan-v2-preflight-20260922`에 다음 일회성 컨테이너를 만들었다.

- `gongguhan-v2-dbcheck-20260922`: MySQL 8.0, 운영 dump 복원.
- `gongguhan-v2-redischeck-20260922`: 별도 Redis, 운영 슬롯과 분리.
- `gongguhan-v2-appcheck-20260922`: 새 백엔드 이미지, prod 프로파일, 위 복사본 DB 사용.

앱 검증에는 실제 금융 키·체인 개인키·S3 키를 주입하지 않았다. APP_SCHEDULING_ENABLED=false, APP_ADMIN_SEED_ENABLED=false, RECOMMENDATION_SEED_ENABLED=false, CRYPTO_CONFIRMATION_POLL_ENABLED=false로 배치 및 시드를 차단했다. 네트워크도 외부 통신이 안 되는 격리 구조여서 복사한 거래가 외부에서 재실행되지 않는다.

결과: 7개 Flyway migration 적용 성공, V22, Hibernate validate 통과, 앱 기동 및 health UP. 복사본 users 41, payments 108 유지. 기존 FAILED 환불 2개에는 V18의 execution_claimed=true가 적용되어 과거 결과 불명 거래의 재실행을 막는 상태를 확인했다.

검증 컨테이너 3개는 검증 후 모두 중지했다. 삭제하지 않아 자료를 재확인할 수 있지만 불필요한 장기 보관과 개인정보 복제본 관리에는 주의해야 한다. 운영 컨테이너의 중지와 혼동하지 않는다.

## 5. 실제 전환 순서와 검증

먼저 기존 서비스가 실행되는 동안 Compose로 backend/nginx/ai 이미지를 빌드했다. 성공 후 nginx와 backend만 정상 종료 대기 시간을 두고 중지했다. 최종 DB dump를 확인한 뒤 새 backend/ai를 기동했다. backend health UP, AI health UP 확인 후 새 nginx를 기동했다.

Nginx 기동 직후 첫 HTTPS 요청은 아직 포트가 준비되지 않아 연결 거절이 났다. 컨테이너 로그와 상태를 확인하고 재조회한 결과 정상 200이었다. 이를 테스트 성공으로 덮어쓰지 않고 일시적 기동 전 관측 실패로 구분한다.

| 확인 | 결과 |
| --- | --- |
| 운영 flyway_schema_history | V1~V22 전부 success=1 |
| `/`, `/login`, `/group-buys` | HTTPS 200 |
| `/api/v1/categories` | 200 |
| `/api/v1/group-buys` | 200 |
| `/api/v1/group-buys/277` | 200 |
| `/api/v1/actuator/health` | 200, UP |
| 실제 브라우저 | 메인→공동구매 목록→277번 상세 이동, 상품 사진·금액·참여 수 표시 확인 |

브라우저 확인은 기존 세션으로 조회만 했다. 공동구매 참여 버튼, 결제 승인, 환불 재시도, 정산 실행 버튼은 누르지 않았다. 로그인 페이지의 200은 새 계정으로 로그인까지 시험했다는 뜻이 아니다.

실제 배포 후 건수는 users 41, products 279, group_buys 292, payments 108로 배포 전과 같았다. refunds는 10→11로 증가했으며 아래 자동 배치 동작 때문이다. 모든 테이블/모든 열의 전후 체크섬 비교를 수행했다는 의미는 아니다.

## 6. 반드시 전달할 환불 복구 1건

운영 앱에서는 기존 스케줄러 설정을 유지했다. 따라서 새 RefundRecoveryScheduler가 기동 후 기존 모집 실패 참여를 발견하여 환불 원장을 생성했다. 수동으로 환불 API를 호출하지 않았더라도 서버 기동은 자동 배치의 재개를 수반한다.

- refund_id=11, participation_id=19, amount=13,000원.
- reason_type=RECRUIT_FAILED, refund_method=ACCOUNT_TRANSFER.
- 상태 FAILED, execution_claimed=false.
- 실패 사유: `플랫폼 출금 계좌가 설정되지 않았습니다.`
- 조회 시 오늘자 ssafy_api_logs는 0건이었다. 해당 건은 출금 계좌 사전 조건에서 차단됐고, 외부 이체 완료로 기록하지 않는다.

이 건은 복구 원장을 생성한 사실과 실제 환불 성공을 구분해야 한다. 출금 계좌를 추정해서 만들거나 설정을 임의로 채우거나 재시도하지 않았다. 관리자/담당자가 플랫폼 계좌 정책과 기존 거래 증거를 확인해야 한다. 기존 FAILED 2건은 V18의 claimed 보호가 유지됐다.

## 7. 복구 시 주의사항과 미검증 범위

문제가 생기면 우선 요청 유입과 변경된 배치를 멈추고, 새로 발생한 거래가 있는지 대조한다. 이후 현재 DB도 별도 보존하고 어떤 상태로 복구할지 결정한다. 최종 배포 직전 dump를 무조건 덮어쓰면 배포 후 거래·로그가 사라진다. 이 문서는 자동 DB 복원 실행을 승인하는 지시가 아니다.

V1 이미지로만 되돌리는 것을 충분한 rollback으로 보지 않는다. V22에서 외래키를 제거했고 V17~V19가 과거 금융 상태 보호값을 갱신한다. 이전 코드와 새 스키마의 호환성, 쓰기 재개 후 데이터 보존을 검토해야 한다. `docker compose down -v`는 사용하지 않는다.

이번에 확보한 추가 근거는 실제 MySQL dump 복원, Flyway/Hibernate 검증, 운영 재기동, 공개 API·화면 smoke 확인이다. 전체 단위·통합 시험을 EC2에서 다시 실행한 것은 아니다. 이미지 Dockerfile은 test를 제외한 빌드이므로 이를 전체 테스트 성공으로 표현하지 않는다.

실 카드/카카오/코인 승인·환불·정산 E2E, S3 업로드/삭제, Redis 슬롯 경합, 결제 승인 중 공구 종료 등 인수인계의 미완료 위험은 여전히 별도 검증/수정 대상이다. 배포 성공이 서비스 전체 무결성이나 모든 결함 해결을 뜻하지 않는다.
