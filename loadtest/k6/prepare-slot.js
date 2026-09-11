// k6 load test for POST /api/v1/group-buys/{groupBuyId}/payments/prepare
//
// Mints JWTs offline in setup() (HS256, matching JwtTokenProvider's claim shape)
// so no real /auth/login round trip is needed per identity. Requires the app to
// be started with the SAME JWT_SECRET value passed to this script (-e JWT_SECRET=...),
// and requires loadtest/seed.sql to have been applied first.
//
// Run identically before and after the Redis gate change to produce a fair
// before/after comparison. See loadtest/README.md for exact commands.
import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const JWT_SECRET = __ENV.JWT_SECRET;
const GROUP_BUY_ID = __ENV.GROUP_BUY_ID || '9000001';
const USER_COUNT = parseInt(__ENV.USER_COUNT || '900', 10);
const USER_ID_BASE = parseInt(__ENV.USER_ID_BASE || '9001000', 10);
const RUN_LABEL = __ENV.RUN_LABEL || 'run';

if (!JWT_SECRET) {
  throw new Error('JWT_SECRET env var is required and must match the running app\'s jwt.secret');
}

// Split success vs reject latency: the whole point of the Redis fast-fail gate
// is that reject_latency should drop sharply after the change while
// success_latency stays roughly flat.
const successLatency = new Trend('success_latency', true);
const rejectLatency = new Trend('reject_latency', true);
const unexpectedErrors = new Counter('unexpected_errors');

function base64url(obj) {
  return encoding.b64encode(JSON.stringify(obj), 'rawurl');
}

// Hand-builds an HS256 JWT matching JwtTokenProvider's claims (sub/role/isSeller/tokenType/iat/exp).
function mintToken(userId) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    sub: String(userId),
    role: 'USER',
    isSeller: false,
    tokenType: 'ACCESS',
    iat: now,
    exp: now + 3600,
  };
  const signingInput = `${base64url(header)}.${base64url(payload)}`;
  const signature = crypto.hmac('sha256', JWT_SECRET, signingInput, 'base64rawurl');
  return `${signingInput}.${signature}`;
}

export const options = {
  scenarios: {
    slot_burst: {
      executor: 'shared-iterations',
      vus: Math.min(USER_COUNT, 300),
      iterations: USER_COUNT,
      maxDuration: '60s',
    },
  },
};

// Mint all tokens once, up front, so crypto work never pollutes per-request latency.
export function setup() {
  const identities = [];
  for (let i = 0; i < USER_COUNT; i++) {
    const userId = USER_ID_BASE + 1 + i; // matches seed.sql: 9001001..9001000+USER_COUNT
    identities.push({ userId, addressId: userId, token: mintToken(userId) });
  }
  return { identities };
}

export default function (data) {
  // shared-iterations gives a globally unique, monotonically increasing index
  // across all VUs, so each seeded identity is used by exactly one request.
  const idx = exec.scenario.iterationInTest;
  const identity = data.identities[idx % data.identities.length];

  const payload = JSON.stringify({ method: 'CARD', addressId: identity.addressId });
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${identity.token}`,
    'Idempotency-Key': `loadtest-${RUN_LABEL}-${identity.userId}`,
  };

  const res = http.post(
    `${BASE_URL}/group-buys/${GROUP_BUY_ID}/payments/prepare`,
    payload,
    { headers }
  );

  if (res.status === 201) {
    successLatency.add(res.timings.duration);
  } else if (res.status === 409) {
    rejectLatency.add(res.timings.duration);
  } else {
    unexpectedErrors.add(1);
  }

  check(res, { 'status is 201 or 409': (r) => r.status === 201 || r.status === 409 });
}
