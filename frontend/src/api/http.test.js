// http.js 테스트: 정상 응답 · 오류 응답(error 코드) · 서버 연결 실패 · JSON 아닌 응답
import assert from 'node:assert/strict';
import { apiRequest, ApiError, NETWORK_ERROR } from './http.js';

const reply = (status, body) => async () => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => {
    if (body === undefined) throw SyntaxError('not json');
    return body;
  },
});

// 정상 응답 → JSON 반환, 요청 본문은 JSON 문자열
let sent;
const ok = await apiRequest('/api/x', { method: 'POST', body: { a: 1 } }, async (url, init) => {
  sent = { url, init };
  return reply(201, { id: 1 })();
});
assert.deepEqual(ok, { id: 1 });
assert.equal(sent.url, '/api/x');
assert.equal(sent.init.method, 'POST');
assert.equal(sent.init.body, '{"a":1}');
assert.equal(sent.init.headers['Content-Type'], 'application/json');

// GET은 본문 · Content-Type 없음
await apiRequest('/api/y', {}, async (url, init) => {
  assert.equal(init.method, 'GET');
  assert.equal(init.body, undefined);
  return reply(200, {})();
});

// 오류 응답 → ApiError(code, message, status)
await assert.rejects(
  apiRequest(
    '/api/x',
    {},
    reply(409, { error: 'DUPLICATE_LOGIN_ID', message: '이미 사용 중인 아이디예요' }),
  ),
  (e) =>
    e instanceof ApiError &&
    e.code === 'DUPLICATE_LOGIN_ID' &&
    e.message === '이미 사용 중인 아이디예요' &&
    e.status === 409,
);

// 서버 연결 실패 → NETWORK_ERROR
await assert.rejects(
  apiRequest('/api/x', {}, async () => {
    throw TypeError('Failed to fetch');
  }),
  (e) => e.code === NETWORK_ERROR && e.status === 0,
);

// JSON이 아닌 오류 응답(프록시 502 등) → NETWORK_ERROR
await assert.rejects(apiRequest('/api/x', {}, reply(502)), (e) => e.code === NETWORK_ERROR);

console.log('PASS: http.js 정상 · 오류 코드 · 연결 실패 · JSON 아닌 응답');
