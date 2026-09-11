-- S15P21B205-128: SSAFY 가맹점 등록 결과를 정책값으로 반영.
--
-- merchantId 40861은 SSAFY 교육용 금융망에 createMerchant로 등록된 '공구한'
-- 가맹점(카테고리 CG-9ca85f66311a23d 생활)이다. 같은 SSAFY 환경을 쓰는 모든
-- 로컬/배포 DB에서 동일한 값이므로 마이그레이션으로 시드한다.
-- 재등록·확인은 backend/scripts/ssafy-create-merchant.mjs 참고.
--
-- 관리자가 운영 중 값을 바꿨을 수 있으므로 빈 값일 때만 채운다.
UPDATE platform_policies
SET policy_value = '40861'
WHERE policy_key = 'SSAFY_MERCHANT_ID'
  AND policy_value = '';
