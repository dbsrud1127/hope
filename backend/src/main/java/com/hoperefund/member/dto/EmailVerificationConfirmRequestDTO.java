package com.hoperefund.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 이메일 인증번호 확인 요청 DTO (React → Spring Boot)
 * 사용자가 메일로 받은 6자리 숫자를 받는다.
 */
public record EmailVerificationConfirmRequestDTO(
        @NotBlank(message = "인증 코드를 입력해 주세요")
                @Pattern(regexp = "^[0-9]{6}$", message = "인증 코드가 맞지 않거나 만료됐어요")
                String code) {

    // 인증번호가 로그에 찍히지 않도록 toString에서 제외
    @Override
    public String toString() {
        return "EmailVerificationConfirmRequestDTO[code=***]";
    }
}
