# 기능별 구현 상태

- 기준일: 2026-11-10 · 브랜치: `feature/member-signup` (**커밋 전** · push 안 함)
- 상태: **작성**(코드 있음) → **테스트**(자동 테스트 통과) → **연결**(React가 API 호출) → **DB**(Oracle 저장 확인)

## 전체 현황

| 기능 | 기능 ID | 작성 | 테스트 | 연결 | DB |
|---|---|---|---|---|---|
| 회원가입 + 중복확인 + 이메일 인증 | F-01-01 | ✅ | ✅ 백 45건 · 프론트 통과 | ✅ 코드 연결 · ⏳ 브라우저 확인 전 | ⏳ 미검증 |
| 로그인 · 회원정보 수정 · 탈퇴 | 확인 필요 | ❌ | - | ❌ (화면은 localStorage) | - |
| 소득 · 카드 · 예측 · 통계 · 챗봇 · 문의 · 관리자 | 확인 필요 | ❌ | - | ❌ | - |

---

## F-01-01 회원가입

### ▶ 다음에 할 일 (순서대로)

1. **실행 설정** (본인이 직접 · `.env` 내용은 커밋 금지)
   - 백엔드 환경변수 `EMAIL_DEV_CODE_ENABLED=true` 추가 → 서버 재시작
     - VS Code `BackendApplication` 실행 시: `.vscode/launch.json`의 `env`
     - `Spring Boot-HopeRefundBackendApplication` 실행 시: `hope/.env`
   - `frontend/.env`에 `VITE_TERMS_VERSION=v1.0` · `VITE_PRIVACY_VERSION=v1.0` · `VITE_FINANCE_VERSION=v1.0` 추가 → `npm run dev` 재시작
2. **API 실행 테스트** (curl · 테스트 아이디 `f0101_test1` 등 → 공용 DB에 실제 저장됨)
3. **브라우저 테스트**: 가입 → 로그인 화면으로 이동 + "회원가입이 완료되었어요" 확인
4. **DB 확인** (SQL Developer): `SELECT MEMBER_ID, LOGIN_ID, EMAIL, NAME, GENDER, EMAIL_VERIFIED_YN, TERMS_VERSION FROM MEMBER WHERE LOGIN_ID LIKE 'f0101%';`
   - 기대: 소문자 저장 · `NAME` NULL · `PASSWORD`는 `$2a$...` · `EMAIL_VERIFIED_YN='Y'`
5. 결과에 따라 이 문서 상태 표 갱신 → 커밋 (`feature/member-signup`)

### API (제안 주소 · 팀 API 명세에 추가 필요)

| 기능 | 요청 | 성공 응답 |
|---|---|---|
| 아이디 중복확인 | `GET /api/auth/members/availability?loginId=` | 200 `{available, message}` |
| 이메일 중복확인 | `GET /api/auth/members/availability?email=` | 200 `{available, message}` |
| 인증번호 발송 | `POST /api/auth/email-verifications` `{email}` | 201 `{verificationId, expiresAt, devCode}` |
| 인증번호 확인 | `POST /api/auth/email-verifications/{verificationId}/confirmation` `{code}` | 200 `{verified, message}` |
| 회원가입 | `POST /api/auth/members` (기존 필드 + `verificationId`) | 201 `{memberId, loginId, email, name(null), role, createdAt}` |

- 오류 응답은 모두 `{"error": "코드", "message": "문구"}`
- 주요 오류 코드: `VALIDATION`(400) · `INVALID_LOGIN_ID`(400) · `UNDER_AGE`(400) · `DUPLICATE_LOGIN_ID` · `DUPLICATE_EMAIL` · `DUPLICATE_MEMBER`(409) · `VERIFICATION_FAILED` · `VERIFICATION_EXPIRED` · `VERIFICATION_LIMIT` · `EMAIL_NOT_VERIFIED` · `SIGNUP_FAILED`(500)

### 처리 흐름

```
React AccountForms.jsx → member-api.js → http.js (fetch, Vite 프록시 /api → :8080)
  → MemberController / EmailVerificationController
  → MemberService.signup: withdrawn_ 차단 → 아이디 중복 → 이메일 중복 → 만 14세 → 비밀번호 → 이메일 인증 확인
  → MemberMapper.insertMember (MEMBER INSERT) → 인증 기록 삭제 → 201
```

### 파일 위치

| 구분 | 파일 |
|---|---|
| Controller | `member/controller/MemberController.java` · `EmailVerificationController.java` (신규) |
| Service | `member/service/MemberService.java` · `EmailVerificationService.java` (신규) |
| DTO | `member/dto/MemberSignup{Request,Response}DTO` · `EmailVerification{Request,ConfirmRequest,Response}DTO` · `MemberAvailabilityResponseDTO` |
| Mapper / SQL | `member/mapper/MemberMapper.java` · `resources/mapper/member/MemberMapper.xml` (변경 없음) |
| 공통 | `common/exception/GlobalExceptionHandler.java` (검증 문구를 필드별 한국어로) · `application.yml` (`member.email-verification.dev-code-enabled`) |
| 테스트 | `MemberControllerTest`(12) · `MemberServiceTest`(12) · `EmailVerificationControllerTest`(5) · `EmailVerificationServiceTest`(8) |
| React | `frontend/src/api/http.js` · `member-api.js` (신규 · 테스트 포함) · `pages/auth/AccountForms.jsx` · `vite.config.js` (프록시) |

### 이메일 인증 방식 (임시 결정)

- 실제 메일은 **보내지 않음** (메일 라이브러리 미추가). 개발 중에는 `EMAIL_DEV_CODE_ENABLED=true`일 때만 응답 `devCode`로 인증번호를 받음 (기본값 false → 운영에서 노출 안 됨)
- 인증 기록은 **서버 메모리**에 저장: 6자리 · 5분 유효 · 5번 틀리면 무효 · 인증 후 30분 안에 가입 · 코드는 BCrypt로 저장
- 서버를 재시작하면 인증 기록이 사라짐 → 인증부터 다시 해야 함
- ERD에 가입 전 인증 테이블이 없어서 이렇게 결정함 (DB 변경 금지)

### 남은 확인 사항 (팀 확인 필요)

- [ ] 메일 발송 방식 (SMTP 계정 · `EmailVerificationService.send`의 TODO 위치에 연결)
- [ ] 새 API 주소 5개를 팀 API 명세에 추가
- [ ] 동의서 버전 실제 값 (지금은 임시 `v1.0`)
- [ ] `MEMBER.DATA_ORIGIN` 허용 값 (임시 `INPUT`) · `GENDER` 제약조건(M/F) 실제 DB 확인
- [ ] `MEMBER_ID` 시퀀스 생기면 `MAX + 1` 교체 — [decisions/0003](decisions/0003-member-id-numbering.md)
- [x] 마케팅 동의: ERD에 칸이 없어 화면에서 제거 (2026-11-11)
- [ ] 서버에 가입한 회원은 아직 **로그인 불가** (로그인은 localStorage · 다음 기능 범위)
