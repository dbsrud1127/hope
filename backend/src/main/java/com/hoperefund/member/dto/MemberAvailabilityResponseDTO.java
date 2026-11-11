package com.hoperefund.member.dto;

/**
 * 아이디 · 이메일 중복확인 응답 DTO (Spring Boot → React)
 * available: 사용 가능 여부 · message: 화면에 보여줄 문구
 */
public record MemberAvailabilityResponseDTO(boolean available, String message) {}
