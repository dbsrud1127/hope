package com.hoperefund.member.dto;

import java.time.Instant;

/**
 * 이메일 인증번호 발송 응답 DTO (Spring Boot → React)
 * verificationId: 인증 확인 · 회원가입 요청에 다시 보내는 인증 번호표 (추측할 수 없는 임의 값)
 * expiresAt: 인증번호 만료 시각
 * devCode: 개발 설정(EMAIL_DEV_CODE_ENABLED=true)일 때만 인증번호를 담는다. 그 외에는 null.
 *          TODO: 메일 발송 구현 후 제거
 */
public record EmailVerificationResponseDTO(
        String verificationId, Instant expiresAt, String devCode) {}
