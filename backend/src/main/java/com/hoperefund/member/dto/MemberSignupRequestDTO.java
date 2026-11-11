package com.hoperefund.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 회원가입 요청 DTO (React → Spring Boot)
 * 화면이 넣어야 하는 값만 받는다. 회원번호 · 권한 · 상태 · 동의 시각은 서버가 정한다.
 * 실명(name)은 받지 않는다 (실명 미수집 · MEMBER.NAME NULL).
 * 칸 길이는 MEMBER 테이블 기준 (LOGIN_ID 50 · EMAIL 100 · *_VERSION 10).
 * message: 형식 검사 실패 시 화면에 보여줄 문구 (GlobalExceptionHandler가 응답에 사용)
 */
public record MemberSignupRequestDTO(
        // 아이디: 영문·숫자·밑줄 4~50자 = 50바이트 이내 (서버에서 소문자로 바꿔 저장)
        @NotBlank(message = "아이디를 입력해 주세요")
                @Pattern(regexp = "^[A-Za-z0-9_]{4,50}$", message = "아이디는 영문·숫자·밑줄 4~50자로 입력해 주세요")
                String loginId,

        // 이메일 (서버에서 소문자로 바꿔 저장)
        @NotBlank(message = "이메일을 입력해 주세요")
                @Email(message = "이메일 형식을 확인해 주세요")
                @Size(max = 100, message = "이메일 형식을 확인해 주세요")
                String email,

        // 비밀번호 원문: 서버에서 BCrypt로 암호화 (BCrypt 입력 한도 72바이트)
        @NotBlank(message = "비밀번호를 입력해 주세요")
                @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 입력해 주세요")
                String password,

        // 비밀번호 확인
        @NotBlank(message = "비밀번호 확인을 입력해 주세요") String passwordConfirm,

        // 생년월일 yyyy-MM-dd
        @NotNull(message = "생년월일을 입력해 주세요") @Past(message = "생년월일을 확인해 주세요") LocalDate birthDate,

        // 성별 M/F (일반 회원 필수 · CK_MEMBER_GENDER)
        @NotBlank(message = "성별을 선택해 주세요") @Pattern(regexp = "^[MF]$", message = "성별을 선택해 주세요")
                String gender,

        // 필수 동의 (이용약관 · 개인정보 · 금융 정보 저장)
        @NotNull(message = "이용약관에 동의해야 가입할 수 있어요") @AssertTrue(message = "이용약관에 동의해야 가입할 수 있어요")
                Boolean termsAgreed,
        @NotNull(message = "개인정보 수집·이용에 동의해야 가입할 수 있어요")
                @AssertTrue(message = "개인정보 수집·이용에 동의해야 가입할 수 있어요")
                Boolean privacyAgreed,
        @NotNull(message = "소득·카드 내역 저장에 동의해야 가입할 수 있어요")
                @AssertTrue(message = "소득·카드 내역 저장에 동의해야 가입할 수 있어요")
                Boolean financeAgreed,

        // 동의한 동의서 버전 (화면의 VITE_*_VERSION 값)
        @NotBlank(message = "동의서 버전 정보가 없어요. 관리자에게 문의해 주세요")
                @Size(max = 10, message = "동의서 버전 정보가 올바르지 않아요")
                String termsVersion,
        @NotBlank(message = "동의서 버전 정보가 없어요. 관리자에게 문의해 주세요")
                @Size(max = 10, message = "동의서 버전 정보가 올바르지 않아요")
                String privacyVersion,
        @NotBlank(message = "동의서 버전 정보가 없어요. 관리자에게 문의해 주세요")
                @Size(max = 10, message = "동의서 버전 정보가 올바르지 않아요")
                String financeVersion,

        // 이메일 인증 번호표 (POST /api/auth/email-verifications 응답의 verificationId)
        @NotBlank(message = "이메일 인증을 완료해 주세요") String verificationId) {

    // 비밀번호 원문 · 인증 번호표가 로그에 찍히지 않도록 toString에서 제외
    @Override
    public String toString() {
        return "MemberSignupRequestDTO[loginId=" + loginId + "]";
    }
}
