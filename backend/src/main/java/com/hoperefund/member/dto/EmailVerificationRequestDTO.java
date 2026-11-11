package com.hoperefund.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이메일 인증번호 발송 요청 DTO (React → Spring Boot)
 * 인증번호를 받을 이메일만 받는다.
 */
public record EmailVerificationRequestDTO(
        @NotBlank(message = "이메일을 입력해 주세요")
                @Email(message = "이메일 형식을 확인해 주세요")
                @Size(max = 100, message = "이메일 형식을 확인해 주세요")
                String email) {}
