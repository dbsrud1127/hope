// 회원가입(F-01-01) back API 호출 함수입니다.
// 화면 입력값 이름을 API 이름으로 바꾸는 일도 여기서 합니다 (화면 username → API loginId 등).
// TODO: 중복확인 · 이메일 인증 주소는 팀 API 명세에 없는 제안 주소입니다. 명세 추가 필요.
import { apiRequest } from './http.js';
import { consentVersions } from '../config/consent-versions.js';
import { ageOn } from './account-service.js';

// 화면 성별 버튼 값 → API·DB 값 (MEMBER.GENDER M/F)
export const GENDER_CODE = { 남성: 'M', 여성: 'F' };

// 가입 버튼을 누르기 전 화면 검사: 문제가 있으면 안내 문구, 없으면 ''를 돌려줍니다.
// 서버도 같은 항목을 다시 검사합니다 (화면 검사만으로 가입되지 않음).
export function signupFormError(form, now = new Date()) {
  if (!form.username.trim()) return '아이디를 입력해 주세요';
  if (form.checkedUsername !== form.username) return '아이디 중복확인을 해주세요';
  if (form.password.length < 8) return '비밀번호는 8자 이상 입력해 주세요';
  if (form.password !== form.confirm) return '비밀번호 확인이 일치하지 않아요';
  if (!form.email.trim()) return '이메일을 입력해 주세요';
  if (form.checkedEmail !== form.email) return '이메일 중복확인을 해주세요';
  if (!form.verificationId) return '이메일 인증을 완료해 주세요';
  if (!form.birth) return '생년월일을 입력해 주세요';
  try {
    if (ageOn(form.birth, now) < 14) return '만 14세 이상만 가입할 수 있어요';
  } catch {
    return '생년월일을 확인해 주세요';
  }
  if (!GENDER_CODE[form.gender]) return '성별을 선택해 주세요';
  if (!form.terms) return '이용약관에 동의해야 가입할 수 있어요';
  if (!form.privacy) return '개인정보 수집·이용에 동의해야 가입할 수 있어요';
  if (!form.financial) return '소득·카드 내역 저장에 동의해야 가입할 수 있어요';
  return '';
}

// 화면 입력값 → 회원가입 API 요청 본문
export function toSignupRequest(form, versions = consentVersions) {
  return {
    loginId: form.username.trim(),
    email: form.email.trim(),
    password: form.password,
    passwordConfirm: form.confirm,
    birthDate: form.birth, // <input type="date"> 값 yyyy-MM-dd
    gender: GENDER_CODE[form.gender] || '',
    termsAgreed: Boolean(form.terms),
    privacyAgreed: Boolean(form.privacy),
    financeAgreed: Boolean(form.financial),
    termsVersion: versions.TERMS_VERSION,
    privacyVersion: versions.PRIVACY_VERSION,
    financeVersion: versions.FINANCE_VERSION,
    verificationId: form.verificationId,
  };
}

export function createMemberApi(request = apiRequest) {
  return {
    // 아이디 중복확인 → {available, message}
    checkLoginId: (loginId) =>
      request(`/api/auth/members/availability?loginId=${encodeURIComponent(loginId.trim())}`),
    // 이메일 중복확인 → {available, message}
    checkEmail: (email) =>
      request(`/api/auth/members/availability?email=${encodeURIComponent(email.trim())}`),
    // 인증번호 발송 → {verificationId, expiresAt, devCode}
    sendEmailCode: (email) =>
      request('/api/auth/email-verifications', { method: 'POST', body: { email: email.trim() } }),
    // 인증번호 확인 → {verified, message}
    confirmEmailCode: (verificationId, code) =>
      request(`/api/auth/email-verifications/${encodeURIComponent(verificationId)}/confirmation`, {
        method: 'POST',
        body: { code },
      }),
    // 회원가입 → {memberId, loginId, email, name, role, createdAt}
    signUp: (form) => request('/api/auth/members', { method: 'POST', body: toSignupRequest(form) }),
  };
}

export const memberApi = createMemberApi();
