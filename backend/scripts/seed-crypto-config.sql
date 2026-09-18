-- 코인 결제 설정 시드 (배포 환경용)
--
-- V1 마이그레이션은 crypto_assets·crypto_wallets 를 플레이스홀더(0x000...0)로 넣습니다.
-- 실제 컨트랙트와 지갑 주소는 환경마다 다르고, 팀원이 각자 만든 테스트넷 지갑을
-- 마이그레이션에 박아 두면 다른 환경에서 잘못된 주소로 돈이 나갑니다.
--
-- 그래서 주소는 마이그레이션이 아니라 이 스크립트로 환경별로 넣습니다.
-- 값을 채우지 않으면 CryptoQuoteService 가 isPlaceholder() 로 막아
-- "수취 지갑 주소가 아직 설정되지 않았습니다" 로 거부합니다 - 조용히 잘못되지는
-- 않지만 코인 결제가 동작하지 않습니다.
--
-- 실행:
--   docker compose -f docker-compose.prod.yml exec -T mysql \
--     mysql -u<USER> -p<PASSWORD> <DB> < backend/scripts/seed-crypto-config.sql
--
-- 여러 번 실행해도 안전합니다(UPDATE 만 합니다).

-- ---------------------------------------------------------------
-- 1. 토큰 컨트랙트
-- ---------------------------------------------------------------
-- Polygon Amoy 에 배포한 테스트용 ERC-20 (docs/contracts/GongguhanTestUSDT.sol).
-- decimals 는 6 입니다. 컨트랙트와 DB 가 어긋나면 금액이 10^12 배 틀어집니다.
UPDATE crypto_assets
   SET token_contract = '0x2dae8b4f165b56e8bdec4cb0bc0d8f19c7466a7c',
       decimals       = 6,
       enabled        = TRUE
 WHERE symbol = 'USDT'
   AND chain_id = 80002;

-- ---------------------------------------------------------------
-- 2. 수취 지갑 (구매자가 보낸 코인을 받는 곳)
-- ---------------------------------------------------------------
-- 이 지갑의 개인키는 서버에 두지 않습니다. 받기만 하면 되므로 필요 없습니다.
UPDATE crypto_wallets
   SET address = '0x557BA62Ce10a53320f3B0C50fa9D211c41c6c6c7',
       active  = TRUE
 WHERE purpose = 'RECEIVE'
   AND chain_id = 80002;

-- ---------------------------------------------------------------
-- 3. 환불 지갑 (서버가 여기서 코인을 내보냅니다)
-- ---------------------------------------------------------------
-- 이 주소의 개인키를 CHAIN_REFUND_WALLET_PRIVATE_KEY 환경변수로 넣어야 합니다.
-- 둘이 다르면 기동 로그의 "환불 지갑 준비됨 address=..." 가 이 주소와 달라집니다.
--
-- 지갑에는 두 가지가 있어야 합니다.
--   POL   가스비. 송금 1건에 약 0.0036
--   USDT  환불로 내보낼 토큰
UPDATE crypto_wallets
   SET address = '0x29fe5321D10c1FC627B75858B230be2EDAcc2d70',
       active  = TRUE
 WHERE purpose = 'REFUND'
   AND chain_id = 80002;

-- ---------------------------------------------------------------
-- 확인
-- ---------------------------------------------------------------
SELECT symbol, chain_id, token_contract, decimals, enabled FROM crypto_assets;
SELECT purpose, address, active FROM crypto_wallets ORDER BY purpose;
