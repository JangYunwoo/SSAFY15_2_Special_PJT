#!/usr/bin/env bash
#
# 공구한 EC2 UFW(우분투 방화벽) 하드닝 스크립트 — 멱등(여러 번 실행해도 안전).
#
# 배경:
#   * SSAFY EC2 는 클라우드 보안그룹(SG)에서 22/80/443/tcp1024~65535/udp1024~65535 를 허용한다.
#     SG 는 "넓게" 열려 있으므로, 실제 노출 범위는 서버 안 UFW 로 좁혀야 한다.
#   * 우리 아키텍처는 공개 포트가 80/443(Nginx) + 22(SSH) 뿐이다.
#     backend/ai/mysql/redis 는 도커 내부 네트워크 전용이라 호스트 포트를 열지 않는다.
#
# ⚠️ SSH 잠김 방지: enable 전에 반드시 22 를 먼저 allow 한다. 실행 전 SSH 세션을 2개 이상 열어둘 것.
#
# 사용:  chmod +x ufw-setup.sh && sudo ./ufw-setup.sh

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "root 로 실행하세요:  sudo $0" >&2
  exit 1
fi

echo "[1/5] 기본 정책: 들어오는 것은 차단, 나가는 것은 허용"
ufw default deny incoming
ufw default allow outgoing

echo "[2/5] SSH(22) 허용 — enable 전에 반드시 먼저"
ufw allow 22/tcp

echo "[3/5] HTTP(80)·HTTPS(443) 허용 — 공개 진입점 Nginx"
ufw allow 80/tcp
ufw allow 443/tcp

echo "[4/5] UFW 활성화"
# --force: 대화형 확인(y/n) 없이 진행. 위에서 22 를 이미 허용했으므로 안전.
ufw --force enable

echo "[5/5] 최종 상태"
ufw status verbose

cat <<'NOTE'

------------------------------------------------------------------
확인 사항
  - 열려 있어야 하는 포트: 22/tcp, 80/tcp, 443/tcp  (그 외 전부 차단)
  - backend(8080)/ai(8000)/mysql(3306)/redis(6379) 는 여기에 없어야 정상.
    docker-compose.prod.yml 이 이 포트들을 호스트로 publish 하지 않기 때문.

⚠️ 도커-UFW 함정
  docker 가 'ports:' 로 포트를 publish 하면 iptables 를 직접 조작해 UFW 규칙을
  우회한다. 즉 나중에 어떤 컨테이너에 'ports: 8080:8080' 을 추가하면 UFW 가
  막고 있어도 인터넷에 열린다. 내부 서비스에는 절대 'ports:' 를 추가하지 말고,
  디버깅 목적이라면 '127.0.0.1:8080:8080' 처럼 루프백에만 바인딩할 것.
------------------------------------------------------------------
NOTE
