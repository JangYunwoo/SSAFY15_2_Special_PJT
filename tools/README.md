# 시연 도구

## bank-console.html — 가상 은행 조회 콘솔

공구한은 공동구매 사이트라 은행 앱이 없다. 결제·환불이 실제로 됐는지(카드에 잡혔는지,
환불금이 계좌에 들어왔는지)를 시연할 때 이 콘솔을 **별도 화면**으로 띄워 보여준다.

SSAFY 교육 금융망을 그대로 조회하므로, 공구한에서 카드 결제하면 카드 이용내역에,
환불되면 계좌 거래내역에 실제로 반영된다(백엔드 `GET /users/me/bank/card`,
`/users/me/bank/account` 재사용).

> 파일 위치: `frontend/public/bank-console.html`.
> Vite 가 `frontend/public/*` 을 빌드 산출물 루트로 복사하므로, 배포하면 별도 설정 없이
> `https://j15b205.p.ssafy.io/bank-console.html` 로 열린다. 콘솔은 서빙 오리진을 보고
> API 주소를 자동 결정한다(배포=같은 오리진 `/api/v1`, 로컬=`http://localhost:8080/api/v1`).

### 여는 법

- **배포**: `https://j15b205.p.ssafy.io/bank-console.html` 접속. 같은 오리진의 `/api/v1` 을
  쓰므로 CORS 설정이 필요 없다(Nginx 가 백엔드로 프록시).
- **로컬(택1)**
  1. 정적 서버로 열기 (권장, CORS 안정적)
     ```bash
     cd frontend/public && python -m http.server 8090
     # 브라우저에서 http://localhost:8090/bank-console.html
     ```
  2. 파일 직접 열기: `frontend/public/bank-console.html` 더블클릭 (Origin: null 로 열림)

  로컬 두 경우 모두 백엔드 로컬 프로파일 CORS(`application-local.yml`)에 `http://localhost:8090`,
  `http://127.0.0.1:8090`, `null` 이 허용돼 있어 동작한다. 운영 프로파일엔 없다.

### 쓰는 법
1. 콘솔에서 **공구한 계정 이메일/비밀번호로 로그인**(콘솔이 `/auth/login` 호출).
2. 카드 이용내역(청구예정액)·계좌 잔액/거래내역이 표시된다.
3. 공구한에서 결제/환불한 뒤 **새로고침**을 누르면 반영된 내역이 보인다.

> 참고: SSAFY 카드는 신용카드라 결제해도 계좌에서 즉시 빠지지 않고 카드 청구내역(청구예정액)에
> 잡힌다. 환불은 계좌이체라 계좌 거래내역에 입금으로 찍힌다.
