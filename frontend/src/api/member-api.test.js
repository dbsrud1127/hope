// member-api.js 테스트: 화면 값 → API 요청 이름 변환 · 호출 주소 · 방식
import assert from 'node:assert/strict';
import { createMemberApi, signupFormError, toSignupRequest } from './member-api.js';

const form = {
  username: ' Hope_User ',
  email: ' Hope@Example.com ',
  password: 'password123',
  confirm: 'password123',
  birth: '2000-01-15',
  gender: '여성',
  terms: true,
  privacy: true,
  financial: true,
  marketing: false,
  verificationId: 'verify-1',
};
const versions = { TERMS_VERSION: 'v1.0', PRIVACY_VERSION: 'v1.0', FINANCE_VERSION: 'v1.0' };

// 화면 이름 → API 이름, 성별 남성/여성 → M/F, 마케팅 동의는 보내지 않음 (ERD에 칸 없음)
assert.deepEqual(toSignupRequest(form, versions), {
  loginId: 'Hope_User',
  email: 'Hope@Example.com',
  password: 'password123',
  passwordConfirm: 'password123',
  birthDate: '2000-01-15',
  gender: 'F',
  termsAgreed: true,
  privacyAgreed: true,
  financeAgreed: true,
  termsVersion: 'v1.0',
  privacyVersion: 'v1.0',
  financeVersion: 'v1.0',
  verificationId: 'verify-1',
});
assert.equal(toSignupRequest({ ...form, gender: '남성' }, versions).gender, 'M');
assert.equal(toSignupRequest({ ...form, gender: '' }, versions).gender, '');

// 호출 주소 · 방식 확인
const calls = [];
const api = createMemberApi(async (path, options) => {
  calls.push([path, options]);
  return {};
});
await api.checkLoginId(' hope_user ');
await api.checkEmail('a+b@example.com');
await api.sendEmailCode(' a@example.com ');
await api.confirmEmailCode('verify-1', '123456');
await api.signUp(form);

assert.deepEqual(calls[0], ['/api/auth/members/availability?loginId=hope_user', undefined]);
assert.deepEqual(calls[1], ['/api/auth/members/availability?email=a%2Bb%40example.com', undefined]);
assert.deepEqual(calls[2], [
  '/api/auth/email-verifications',
  { method: 'POST', body: { email: 'a@example.com' } },
]);
assert.deepEqual(calls[3], [
  '/api/auth/email-verifications/verify-1/confirmation',
  { method: 'POST', body: { code: '123456' } },
]);
assert.equal(calls[4][0], '/api/auth/members');
assert.equal(calls[4][1].method, 'POST');
assert.equal(calls[4][1].body.loginId, 'Hope_User');

// 화면 검사: 정상 → '' · 항목별 안내 문구
const now = new Date('2026-11-10T03:00:00Z');
const ready = { ...form, checkedUsername: form.username, checkedEmail: form.email };
assert.equal(signupFormError(ready, now), '');
const cases = [
  [{ checkedUsername: '' }, '아이디 중복확인을 해주세요'],
  [{ password: 'short', confirm: 'short' }, '비밀번호는 8자 이상 입력해 주세요'],
  [{ confirm: 'different1' }, '비밀번호 확인이 일치하지 않아요'],
  [{ checkedEmail: '' }, '이메일 중복확인을 해주세요'],
  [{ verificationId: '' }, '이메일 인증을 완료해 주세요'],
  [{ birth: '' }, '생년월일을 입력해 주세요'],
  [{ birth: '2013-01-01' }, '만 14세 이상만 가입할 수 있어요'],
  [{ birth: '2030-01-01' }, '생년월일을 확인해 주세요'],
  [{ gender: '' }, '성별을 선택해 주세요'],
  [{ terms: false }, '이용약관에 동의해야 가입할 수 있어요'],
  [{ privacy: false }, '개인정보 수집·이용에 동의해야 가입할 수 있어요'],
  [{ financial: false }, '소득·카드 내역 저장에 동의해야 가입할 수 있어요'],
];
for (const [change, message] of cases) {
  assert.equal(signupFormError({ ...ready, ...change }, now), message);
}
// 만 14세 생일 당일(한국 시간)은 가입 가능
assert.equal(signupFormError({ ...ready, birth: '2012-11-10' }, now), '');

console.log('PASS: member-api.js 요청 이름 변환 · 성별 M/F · 주소 · 방식 · 화면 검사');
