/**
 * SSAFY 교육용 금융망 가맹점 등록 스크립트 (S15P21B205-128).
 *
 * 카드 결제 승인(createCreditCardTransaction)이 요구하는 merchantId를 한 번
 * 발급받기 위한 일회성 도구다. 등록 후 출력되는 merchantId를 platform_policies의
 * SSAFY_MERCHANT_ID에 넣는다 (마이그레이션 V4 참고).
 *
 * 실행:  node --env-file=backend/.env backend/scripts/ssafy-create-merchant.mjs [categories|create <categoryId> <merchantName>|list]
 *   categories        카테고리 목록 조회 (categoryId 확인용)
 *   create ...        가맹점 등록
 *   list              등록된 가맹점 목록 조회
 */
const BASE_URL = process.env.SSAFY_BASE_URL ?? 'https://finopenapi.ssafy.io/ssafy/api/v1/edu';
const API_KEY = process.env.SSAFY_API_KEY;
if (!API_KEY) {
  console.error('SSAFY_API_KEY가 없습니다. backend/.env를 --env-file로 지정했는지 확인하세요.');
  process.exit(1);
}

function header(apiName) {
  const now = new Date();
  const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
  const pad = (n, w = 2) => String(n).padStart(w, '0');
  const date = `${kst.getUTCFullYear()}${pad(kst.getUTCMonth() + 1)}${pad(kst.getUTCDate())}`;
  const time = `${pad(kst.getUTCHours())}${pad(kst.getUTCMinutes())}${pad(kst.getUTCSeconds())}`;
  const serial = String(Math.floor(Math.random() * 1_000_000)).padStart(6, '0');
  return {
    apiName,
    transmissionDate: date,
    transmissionTime: time,
    institutionCode: '00100',
    fintechAppNo: '001',
    apiServiceCode: apiName,
    institutionTransactionUniqueNo: date + time + serial,
    apiKey: API_KEY,
  };
}

async function call(apiName, path, body = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ Header: header(apiName), ...body }),
  });
  const data = await res.json().catch(() => null);
  if (!data?.Header) {
    console.error(`HTTP ${res.status} - 응답 형식이 예상과 다릅니다:`, JSON.stringify(data).slice(0, 500));
    process.exit(1);
  }
  if (data.Header.responseCode !== 'H0000') {
    console.error(`${apiName} 실패: ${data.Header.responseCode} ${data.Header.responseMessage}`);
    process.exit(1);
  }
  return data.REC;
}

const [mode = 'categories', categoryId, merchantName] = process.argv.slice(2);

if (mode === 'categories') {
  const rec = await call('inquireCategoryList', '/creditCard/inquireCategoryList');
  for (const c of rec ?? []) console.log(`${c.categoryId}\t${c.categoryName}`);
} else if (mode === 'list') {
  const rec = await call('inquireMerchantList', '/creditCard/inquireMerchantList');
  for (const m of rec ?? []) console.log(`${m.merchantId}\t${m.categoryId}\t${m.merchantName}`);
} else if (mode === 'create') {
  if (!categoryId || !merchantName) {
    console.error('사용법: create <categoryId> <merchantName>');
    process.exit(1);
  }
  const rec = await call('createMerchant', '/creditCard/createMerchant', { categoryId, merchantName });
  console.log('등록 완료:', JSON.stringify(rec));
} else {
  console.error(`알 수 없는 모드: ${mode}`);
  process.exit(1);
}
