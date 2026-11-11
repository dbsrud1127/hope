package com.hoperefund.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.GlobalExceptionHandler;
import com.hoperefund.member.dto.EmailVerificationResponseDTO;
import com.hoperefund.member.service.EmailVerificationService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 이메일 인증 API 테스트
 * 발송 · 확인 요청의 형식 검증과 응답 형식을 확인한다.
 */
@WebMvcTest(EmailVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmailVerificationControllerTest {

    private static final String URL = "/api/auth/email-verifications";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private EmailVerificationService emailVerificationService;

    // 발송 → 201 {verificationId, expiresAt}
    @Test
    void sendReturns201() throws Exception {

        given(emailVerificationService.send("hope@example.com"))
                .willReturn(
                        new EmailVerificationResponseDTO(
                                "verify-1", Instant.parse("2026-11-10T03:05:00Z"), null));

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"hope@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationId").value("verify-1"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    // 잘못된 이메일 형식 → 400, 발송 안 함
    @Test
    void sendInvalidEmailReturns400() throws Exception {

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 형식을 확인해 주세요"));

        verify(emailVerificationService, never()).send(any());
    }

    // 확인 성공 → 200 {verified: true}
    @Test
    void confirmReturns200() throws Exception {

        mockMvc.perform(
                        post(URL + "/verify-1/confirmation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        verify(emailVerificationService).confirm("verify-1", "123456");
    }

    // 6자리 숫자가 아니면 → 400
    @Test
    void confirmInvalidCodeFormatReturns400() throws Exception {

        mockMvc.perform(
                        post(URL + "/verify-1/confirmation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\": \"12ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증 코드가 맞지 않거나 만료됐어요"));
    }

    // 틀린 코드 → 400 VERIFICATION_FAILED
    @Test
    void confirmWrongCodeReturns400() throws Exception {

        willThrow(
                        new BusinessException(
                                HttpStatus.BAD_REQUEST,
                                "VERIFICATION_FAILED",
                                "인증 코드가 맞지 않거나 만료됐어요"))
                .given(emailVerificationService)
                .confirm("verify-1", "000000");

        mockMvc.perform(
                        post(URL + "/verify-1/confirmation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\": \"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VERIFICATION_FAILED"));
    }
}
