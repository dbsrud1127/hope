package com.hoperefund.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.GlobalExceptionHandler;
import com.hoperefund.member.dto.MemberAvailabilityResponseDTO;
import com.hoperefund.member.dto.MemberSignupResponseDTO;
import com.hoperefund.member.service.MemberService;
import java.time.LocalDateTime;
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
 * 회원가입 API 테스트
 * 요청 형식 검증(항목별 문구) · 성공 응답 · 업무 오류 응답 · 중복확인 API를 확인한다.
 */
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemberControllerTest {

    private static final String URL = "/api/auth/members";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MemberService memberService;

    private static final String VALID_BODY =
            """
            {
              "loginId": "hope_user",
              "email": "hope@example.com",
              "password": "password123",
              "passwordConfirm": "password123",
              "birthDate": "2000-01-15",
              "gender": "F",
              "termsAgreed": true,
              "privacyAgreed": true,
              "financeAgreed": true,
              "termsVersion": "v1.0",
              "privacyVersion": "v1.0",
              "financeVersion": "v1.0",
              "verificationId": "verify-1"
            }
            """;

    // 정상 가입 요청 → 201, 명세 응답 필드 · 비밀번호 미포함
    @Test
    void signupReturns201() throws Exception {

        given(memberService.signup(any()))
                .willReturn(
                        new MemberSignupResponseDTO(
                                1L,
                                "hope_user",
                                "hope@example.com",
                                null,
                                "USER",
                                LocalDateTime.of(2026, 11, 10, 12, 0)));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.loginId").value("hope_user"))
                .andExpect(jsonPath("$.email").value("hope@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // 금융 정보 저장 미동의 → 400 VALIDATION + 안내 문구, 서비스 호출 안 함
    @Test
    void missingFinanceAgreementReturns400() throws Exception {

        String body = VALID_BODY.replace("\"financeAgreed\": true", "\"financeAgreed\": false");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("소득·카드 내역 저장에 동의해야 가입할 수 있어요"));

        verify(memberService, never()).signup(any());
    }

    // 이용약관 미동의 → 400
    @Test
    void missingTermsAgreementReturns400() throws Exception {

        String body = VALID_BODY.replace("\"termsAgreed\": true", "\"termsAgreed\": false");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이용약관에 동의해야 가입할 수 있어요"));
    }

    // 개인정보 미동의 → 400
    @Test
    void missingPrivacyAgreementReturns400() throws Exception {

        String body = VALID_BODY.replace("\"privacyAgreed\": true", "\"privacyAgreed\": false");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("개인정보 수집·이용에 동의해야 가입할 수 있어요"));
    }

    // 잘못된 이메일 형식 → 400 + 안내 문구
    @Test
    void invalidEmailReturns400() throws Exception {

        String body = VALID_BODY.replace("hope@example.com", "not-an-email");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("이메일 형식을 확인해 주세요"));
    }

    // 성별 미선택 → 400
    @Test
    void missingGenderReturns400() throws Exception {

        String body = VALID_BODY.replace("\"gender\": \"F\"", "\"gender\": \"\"");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("성별을 선택해 주세요"));
    }

    // 성별 값 범위 밖 → 400
    @Test
    void invalidGenderReturns400() throws Exception {

        String body = VALID_BODY.replace("\"gender\": \"F\"", "\"gender\": \"여성\"");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"));
    }

    // 이메일 인증 번호표 누락 → 400
    @Test
    void missingVerificationIdReturns400() throws Exception {

        String body =
                VALID_BODY.replace("\"verificationId\": \"verify-1\"", "\"verificationId\": \"\"");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증을 완료해 주세요"));
    }

    // 중복 아이디 → 409, GlobalExceptionHandler 형식
    @Test
    void duplicateLoginIdReturns409() throws Exception {

        given(memberService.signup(any()))
                .willThrow(
                        new BusinessException(
                                HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디예요"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_LOGIN_ID"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디예요"));
    }

    // 아이디 중복확인 → 200 {available, message}
    @Test
    void checkLoginIdReturnsAvailability() throws Exception {

        given(memberService.checkLoginId("hope_user"))
                .willReturn(new MemberAvailabilityResponseDTO(false, "이미 사용 중인 아이디예요"));

        mockMvc.perform(get(URL + "/availability").param("loginId", "hope_user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디예요"));
    }

    // 이메일 중복확인 → 200
    @Test
    void checkEmailReturnsAvailability() throws Exception {

        given(memberService.checkEmail("hope@example.com"))
                .willReturn(new MemberAvailabilityResponseDTO(true, "사용 가능한 이메일이에요"));

        mockMvc.perform(get(URL + "/availability").param("email", "hope@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    // 중복확인에 값이 없거나 둘 다 보내면 → 400
    @Test
    void checkAvailabilityNeedsExactlyOneParam() throws Exception {

        mockMvc.perform(get(URL + "/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"));

        mockMvc.perform(get(URL + "/availability").param("loginId", "a").param("email", "b"))
                .andExpect(status().isBadRequest());
    }
}
