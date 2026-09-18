# 공구한 배포 (deploy/)

단일 EC2(`j15b205.p.ssafy.io`)에 **Nginx + 백엔드 + AI + MySQL + Redis** 를 Docker Compose 로 올리는 산출물 모음입니다.
설계 배경·근거·대안 비교는 [`../docs/infra/서버_아키텍처_설계서.md`](../docs/infra/서버_아키텍처_설계서.md) 를 먼저 읽으세요.

## 구성 파일

| 파일 | 역할 |
|---|---|
| `docker-compose.prod.yml` | 전체 서비스 오케스트레이션. 공개 포트는 Nginx(80/443)뿐 |
| `backend.Dockerfile` | Spring Boot 멀티스테이지 빌드(JDK21 빌드 → JRE21 실행) |
| `ai.Dockerfile` | FastAPI(uvicorn) 이미지 |
| `frontend.Dockerfile` | Vite 정적 빌드 → Nginx 서빙 + `/api` 프록시 |
| `nginx/default.conf` | TLS 종료 · SPA 서빙 · `/api` 리버스 프록시 · 보안 헤더 |
| `env/*.env.example` | 서비스별 환경변수 템플릿(실제 값은 `.env` 로 복사, git 제외) |
| `scripts/ufw-setup.sh` | 방화벽 하드닝(22/80/443만 개방) |

## 배포 절차 (요약)

### 0. 사전 준비 (로컬)
- `J15B205T.pem` 준비(권한 `chmod 400`). git 에는 이미 ignore 처리됨.
- GCP 서비스계정 키(JSON) 발급: Vertex AI User 권한. → 서버 `deploy/secrets/gcp-sa.json` 으로 업로드.

### 1. 서버 접속 & 도커 설치
```bash
ssh -i J15B205T.pem ubuntu@j15b205.p.ssafy.io
# docker / docker compose plugin 설치 (없다면)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu   # 재로그인 후 sudo 없이 docker 사용
```

### 2. 방화벽
```bash
cd <repo>/deploy && chmod +x scripts/ufw-setup.sh && sudo ./scripts/ufw-setup.sh
```

### 3. 환경변수/시크릿 채우기
```bash
cd deploy
cp env/backend.env.example env/backend.env   # 값 채우기
cp env/mysql.env.example   env/mysql.env      # DB 비번은 backend.env 와 일치
cp env/ai.env.example      env/ai.env
mkdir -p secrets && mv ~/gcp-sa.json secrets/gcp-sa.json
# ⚠️ AI 컨테이너는 비-root(app) 유저로 돌아 호스트 uid(1000)와 다르다. 0600 이면 컨테이너가
#    키를 못 읽어 Gemini 인증이 PermissionError 로 실패한다. 컨테이너가 읽도록 644 로 둔다.
chmod 644 secrets/gcp-sa.json
```

### 4. TLS 인증서 (최초 1회)
`nginx/default.conf` 는 인증서 경로를 참조하므로, 발급 전에는 임시 자체서명으로 기동하거나 아래로 발급합니다.
```bash
# 80 포트만 먼저 여는 임시 nginx 로 HTTP-01 챌린지 통과 후 발급 (자세한 방법은 설계서 6장)
docker compose -f docker-compose.prod.yml run --rm certbot certonly \
  --webroot -w /var/www/certbot -d j15b205.p.ssafy.io --agree-tos -m <이메일>
```

### 5. 기동
```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
```

### 5-1. 코인 결제 설정 시드 (첫 배포 시 1회) 🔴

V1 마이그레이션은 코인 컨트랙트·지갑 주소를 **플레이스홀더(`0x000...0`)** 로 넣는다.
주소는 환경마다 달라, 누군가의 테스트넷 지갑을 마이그레이션에 박으면 다른 환경에서
잘못된 주소로 돈이 나가기 때문이다.

```bash
docker compose -f docker-compose.prod.yml exec -T mysql   mysql -u<USER> -p<PASSWORD> <DB> < ../backend/scripts/seed-crypto-config.sql
```

실행하면 현재 값이 출력된다. `token_contract` 와 두 지갑 주소가 `0x000...0` 이
아니어야 한다.

> **빼먹어도 서버는 정상으로 뜬다.** 결제 단계에서 "수취 지갑 주소가 아직 설정되지
> 않았습니다" 로 드러난다.

REFUND 지갑에는 **POL(가스비, 송금 1건당 약 0.0036)과 USDT** 가 있어야 하고,
`CHAIN_REFUND_WALLET_PRIVATE_KEY` 는 그 주소의 개인키여야 한다.
기동 로그의 `환불 지갑 준비됨 address=...` 로 짝을 확인한다.

### 6. 검증
```bash
curl -k https://j15b205.p.ssafy.io/api/v1/actuator/health   # {"status":"UP"}
# 브라우저: https://j15b205.p.ssafy.io
```

## 자주 쓰는 명령
```bash
docker compose -f docker-compose.prod.yml logs -f backend   # 로그
docker compose -f docker-compose.prod.yml up -d --build backend  # 백엔드만 재배포
docker compose -f docker-compose.prod.yml down              # 전체 정지(볼륨 유지)
```
