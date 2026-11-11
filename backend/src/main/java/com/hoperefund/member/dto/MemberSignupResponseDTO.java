package com.hoperefund.member.dto;

import java.time.LocalDateTime;

/**
 * 회원가입 성공 응답 DTO (Spring Boot → React)
 * API 명세 기준: memberId, loginId, email, name, role, createdAt
 * 비밀번호 등 민감정보는 돌려주지 않는다. name은 실명 미수집이라 항상 null.
 */
public record MemberSignupResponseDTO(
        Long memberId,
        String loginId,
        String email,
        String name,
        String role,
        LocalDateTime createdAt) {}
