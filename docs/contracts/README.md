# ERC-20 테스트 토큰 배포 가이드

> 코인 결제를 실제로 시연하려면 Polygon Amoy 테스트넷에 ERC-20 토큰이 필요합니다.
> **Remix(브라우저)** 로 배포하므로 Node·Hardhat 같은 도구를 설치할 필요가 없습니다.
>
> 소요 시간 약 30분. 대부분 MetaMask 설정과 faucet 대기입니다.

---

## 왜 필요한가

백엔드는 이미 코인 결제 전체 흐름이 구현돼 있습니다(CRYP-01~04). 그런데 DB 의
토큰 컨트랙트 주소와 수취 지갑 주소가 **자리표시자(`0x0000...0000`)** 입니다.

```sql
crypto_assets.token_contract  = '0x0000000000000000000000000000000000000000'
crypto_wallets.address        = '0x0000000000000000000000000000000000000000'
```

이 상태에서는 두 가지가 막힙니다.

| 막히는 것 | 이유 |
|---|---|
| Quote 발급 (CRYP-02) | 수취 지갑이 영주소면 서버가 발급을 거부합니다. 구매자가 소각 주소로 송금하는 것을 막기 위한 장치입니다 |
| 실제 송금·검증 (CRYP-03) | 보낼 토큰 자체가 없습니다 |

즉 **"구매자가 실제로 USDT 를 보내 결제가 완료되는" 장면을 아직 시연할 수 없습니다.**
코드 경로는 실제 Amoy 체인으로 검증했지만(도용 차단·롤백·컨펌 폴링), 정상 입금
흐름만 미검증입니다.

---

## 1단계 — MetaMask 에 Polygon Amoy 추가

MetaMask 설정 → 네트워크 → 네트워크 추가 → 직접 추가

| 항목 | 값 |
|---|---|
| 네트워크 이름 | `Polygon Amoy` |
| RPC URL | `https://polygon-amoy-bor-rpc.publicnode.com` |
| 체인 ID | `80002` |
| 통화 기호 | `POL` |
| 블록 탐색기 | `https://amoy.polygonscan.com` |

> **RPC URL 주의.** 공식 URL(`https://rpc-amoy.polygon.technology`)은 SSAFY 망에서
> DNS 해석이 안 됩니다. 위 `publicnode.com` 주소를 쓰세요.
> `https://polygon-amoy.drpc.org` 도 동작합니다.

## 2단계 — 가스비용 테스트 POL 받기

https://faucet.polygon.technology 에서 Amoy 네트워크를 골라 지갑 주소를 넣습니다.

배포와 mint 에 가스가 들어가므로 **최소 0.1 POL** 은 있어야 합니다. 수 분 걸립니다.

## 3단계 — 지갑 3개 준비

MetaMask 에서 계정을 추가해 세 개를 만듭니다. **각각 무슨 용도인지 메모해 두세요.**

| 용도 | 설명 | 개인키 필요 |
|---|---|---|
| 배포자 | 컨트랙트를 배포하고 토큰을 mint | 예 (MetaMask 안에만) |
| **수취(RECEIVE)** | 구매자 결제를 받는 플랫폼 지갑 | **아니오** |
| **환불(REFUND)** | 환불 송금용 플랫폼 지갑 | 예 (`.env` 에 넣어야 함) |

> **수취 지갑은 서버가 서명할 일이 없어 개인키가 아예 필요 없습니다.** 받기만 하기
> 때문입니다. 환불 지갑만 서버가 서명해야 해서 개인키가 필요합니다.
>
> **개인키는 DB 에 절대 넣지 않습니다.** `.env` 의 `CHAIN_REFUND_WALLET_PRIVATE_KEY`
> 에만 둡니다. DB 가 유출돼도 자금이 빠져나가지 않게 하는 분리입니다.

배포자와 수취 지갑을 같게 써도 동작하지만, 나누는 편이 흐름을 이해하기 쉽습니다.

## 4단계 — Remix 에서 배포

1. https://remix.ethereum.org 접속
2. 왼쪽 파일 탐색기에서 `contracts` 폴더에 새 파일 생성 → `GongguhanTestUSDT.sol`
3. 이 폴더의 [`GongguhanTestUSDT.sol`](./GongguhanTestUSDT.sol) 내용을 그대로 붙여넣기
4. 왼쪽 **Solidity Compiler** 탭
   - Compiler: `0.8.20` 이상
   - **Compile** 클릭 → 초록 체크 확인
   - OpenZeppelin import 는 Remix 가 자동으로 가져옵니다
5. 왼쪽 **Deploy & Run Transactions** 탭
   - ENVIRONMENT: **`Browser Extension`** 선택
     - Remix 가 예전 이름 `Injected Provider - MetaMask` 를 이렇게 바꿨습니다.
       스크린샷이나 블로그에 옛 이름이 남아 있어도 같은 항목입니다.
     - 기본값은 `Remix VM` 입니다. **이건 브라우저 안의 가짜 체인이라 그대로 배포하면
       Amoy 에 아무것도 올라가지 않습니다.** 반드시 바꾸세요.
   - MetaMask 가 열리면 **Polygon Amoy** 네트워크인지 확인
   - CONTRACT: `GongguhanTestUSDT` 선택
   - 생성자 인자 두 개를 입력합니다

```
_INITIALHOLDER : 배포자 지갑 주소 (0x...)
_INITIALSUPPLY : 1000000000000
```

> `_INITIALHOLDER` 를 비워 두고 Deploy 하면 `invalid address` 로 즉시 거부됩니다.
> 트랜잭션이 나가기 전에 막히므로 가스는 쓰지 않습니다. 주소를 채우고 다시 누르세요.

> `1000000000000` 은 **1,000,000 USDT** 입니다.
> decimals 가 6 이므로 `1,000,000 × 10^6` 입니다. 자리수를 세어 확인하세요.

6. **Deploy** 클릭 → MetaMask 서명
7. 배포되면 왼쪽 아래 **Deployed Contracts** 에 주소가 나옵니다. **복사해 두세요.**

## 5단계 — 구매자 지갑에 토큰 채우기

Remix 의 Deployed Contracts 에서 `mint` 를 펼쳐 호출합니다.

```
to    : 구매자로 쓸 지갑 주소
value : 100000000        (= 100 USDT)
```

> **입력란 이름이 `amount` 가 아니라 `value (uint256)` 로 보입니다.** OpenZeppelin
> ERC20 의 파라미터 이름을 Remix 가 그대로 읽어 표시하는 것이라 정상입니다.
>
> 이걸 화면 아래쪽의 **`Value` / `wei` 입력란과 혼동하지 마세요.** 그쪽은 함께 보낼
> 네이티브 코인(POL) 금액이며 **반드시 0 으로 둬야 합니다.** ERC-20 전송은 토큰
> 컨트랙트가 장부를 옮기는 것이라 POL 을 실어 보낼 필요가 없습니다.

MetaMask 에서 토큰이 보이게 하려면: MetaMask → 토큰 가져오기 → 컨트랙트 주소 붙여넣기.
심볼 `USDT`, 소수점 `6` 이 자동으로 채워지는지 확인하세요.

## 6단계 — 배포 결과 확인

https://amoy.polygonscan.com 에서 컨트랙트 주소를 검색해 트랜잭션이 보이는지 봅니다.

---

## 7단계 — DB 에 반영

**여기서 값을 잘못 넣으면 자금이 사라집니다.** 주소를 두 번 확인하세요.

```sql
-- 토큰 컨트랙트 주소
UPDATE crypto_assets
   SET token_contract = '0x여기에_배포한_컨트랙트_주소'
 WHERE symbol = 'USDT' AND chain_id = 80002;

-- 수취 지갑 (구매자 결제를 받는 곳)
UPDATE crypto_wallets
   SET address = '0x여기에_수취_지갑_주소'
 WHERE purpose = 'RECEIVE' AND chain_id = 80002;

-- 환불 지갑
UPDATE crypto_wallets
   SET address = '0x여기에_환불_지갑_주소'
 WHERE purpose = 'REFUND' AND chain_id = 80002;
```

### 스캔 커서를 현재 블록으로 올리기

```sql
UPDATE chain_scan_cursors
   SET last_scanned_block = <현재_블록_번호>
 WHERE chain_id = 80002;
```

현재 블록 번호 확인:

```bash
curl -sS -X POST https://polygon-amoy-bor-rpc.publicnode.com \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'
```

응답은 16진수입니다. `0x2cea6d3` → 10진수로 변환해서 넣으세요.

> **왜 필요한가.** 시드값이 `0` 입니다. 이벤트 스캔이 0번 블록부터 훑으면 수백만
> 블록을 처리하려 들어 사실상 멈춥니다. 배포 시점 근처로 올려두세요.
> (이벤트 스캔은 아직 미구현이지만, 구현 전에 이 값을 맞춰두는 편이 안전합니다)

### 환불 지갑 개인키를 `.env` 에

```
CHAIN_REFUND_WALLET_PRIVATE_KEY=0x여기에_환불_지갑_개인키
```

MetaMask → 계정 세부정보 → 개인키 표시 로 확인합니다.
**이 값은 절대 커밋하지 않습니다.** `.env` 는 `.gitignore` 대상입니다.

환불 지갑에도 **테스트 POL(가스비)** 와 **테스트 USDT** 가 있어야 환불 송금이 됩니다.
`mint` 로 환불 지갑에도 토큰을 채워두세요.

---

## 8단계 — 실제 결제 시연

```bash
docker compose up -d
cd backend
CRYPTO_RPC_URL=https://polygon-amoy-bor-rpc.publicnode.com ./gradlew bootRun
```

1. 구매자로 로그인 → 공동구매 참여 → 결제수단 코인 선택
2. Quote 발급 (CRYP-02) → 응답의 `assetAmount` 와 `depositAddress` 확인
3. MetaMask 에서 그 주소로 **정확히 그 수량** 을 전송
4. 받은 txHash 를 CRYP-03 에 제출
5. 스케줄러가 5초마다 컨펌을 확인 → 5개 도달 시 결제 완료
6. CRYP-04 로 `phase` 가 `COMPLETED` 로 바뀌는지 확인

> **수량은 정확히 일치해야 합니다.** 1 wei 라도 적으면 언더페이로 거부됩니다.
> Quote 응답의 `assetAmount` 문자열을 그대로 쓰세요.

---

## 자주 겪는 문제

| 증상 | 원인 | 해결 |
|---|---|---|
| Remix 컴파일 실패 (import) | OpenZeppelin 다운로드 실패 | 잠시 후 재시도. 사내망이면 브라우저 프록시 확인 |
| Deploy 버튼이 회색 | ENVIRONMENT 가 지갑에 연결돼 있지 않음 | `Browser Extension` 선택 |
| 배포는 됐는데 Amoy 에 흔적이 없음 | ENVIRONMENT 가 `Remix VM` 이었음 | `Browser Extension` 으로 바꿔 재배포 |
| 새로고침 후 `Deployed Contracts` 가 비어 있음 | Remix 는 배포 목록을 저장하지 않음 | **`Add a deployed contract`** 에 컨트랙트 주소를 붙여 다시 붙임 |
| 함수를 불러도 반응이 없음 | Remix 창을 여러 개 열어 다른 창을 보고 있음 | 창을 하나만 남기고 진행 |
| faucet 이 `already requested` | 이미 지급됐을 수 있음 | MetaMask 잔액을 먼저 확인. POL 이 들어와 있으면 그대로 진행 |
| `insufficient funds` | 가스비 POL 부족 | faucet 에서 더 받기 |
| MetaMask 에 토큰이 안 보임 | 토큰 가져오기 안 함 | 컨트랙트 주소로 수동 추가 |
| Quote 발급이 500 | 수취 지갑이 아직 영주소 | 7단계 DB 반영 확인 |
| 입금했는데 언더페이 거부 | 수량 불일치 또는 decimals 불일치 | `crypto_assets.decimals` 와 컨트랙트 `decimals()` 가 둘 다 6 인지 확인 |
| 컨펌이 안 올라감 | RPC 연결 실패 | 앱 로그에서 `EXTERNAL_SERVICE_UNAVAILABLE` 확인, RPC URL 교체 |

---

## 배포 후 기록해 둘 값

나중에 재현하거나 팀원에게 공유할 때 필요합니다.

> 이 표에는 **주소만** 적고 개인키는 적지 마세요. 이 파일은 git 에 커밋됩니다.

### 2026-09-11 배포 (Polygon Amoy, chainId 80002)

| 항목 | 값 |
|---|---|
| 토큰 컨트랙트 | `0x2dae8b4f165b56e8bdec4cb0bc0d8f19c7466a7c` |
| 심볼 / decimals | `USDT` / `6` |
| 초기 발행량 | `1000000000000` (= 1,000,000 USDT) |
| 배포 블록 | `47265522` |
| 배포자 지갑 | `0x3989ed99f2d2cb64106ed71d8cdaaeecbd671cd6` |
| 수취(RECEIVE) 지갑 | `0x557BA62Ce10a53320f3B0C50fa9D211c41c6c6c7` |
| 환불(REFUND) 지갑 | `0x3E35A3C44246B0073E4723a7d1c47c85481EE81A` |

컨트랙트를 온체인으로 직접 확인한 결과입니다.

```
name()        Gongguhan Test USDT
symbol()      USDT
decimals()    6
totalSupply() 1000000000000
```

> `decimals()` 가 `6` 인지 꼭 확인하세요. `crypto_assets.decimals` 와 다르면 금액
> 대조가 전부 어긋나 정상 입금도 언더페이로 거부됩니다.

### 결제 E2E 검증 (동일 일자)

| 항목 | 값 |
|---|---|
| 트랜잭션 | `0xa18e6822603f143775eb06fc6597ccb06b8256b21aedcd4bdb24f66c85f22134` |
| 블록 | `47281903` |
| 송금액 | `39528990` (raw) = 39.528990 USDT |
| 결과 | `phase=COMPLETED`, `payments.status=PAID`, 에스크로 `HELD` |

구매자가 실제로 토큰을 보내 결제가 완료되고 에스크로까지 잡히는 경로를 확인했습니다.

---

## 참고 — 나중에 도구를 붙일 때

지금은 Remix 로 한 번 배포하고 끝이라 이 폴더에 `.sol` 파일만 둡니다.
컨트랙트를 자주 수정하거나 테스트 코드를 붙이게 되면 Hardhat/Foundry 를 도입하고,
최상위 `contracts/` 폴더로 옮기는 편이 낫습니다. 그때는 `GIT_CONVENTION.md` 의
폴더 구조도 함께 갱신해야 합니다.
