package com.hoperefund.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.dto.MemberAvailabilityResponseDTO;
import com.hoperefund.member.dto.MemberInsertDTO;
import com.hoperefund.member.dto.MemberSignupRequestDTO;
import com.hoperefund.member.dto.MemberSignupResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원가입 Service 테스트
 * DB 없이 Mapper · 이메일 인증을 대신해 업무 검증 · 암호화 · 저장 값을 확인한다.
 */
class MemberServiceTest {

    private MemberMapper memberMapper;
    private PasswordEncoder passwordEncoder;
    private EmailVerificationService emailVerificationService;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        passwordEncoder = new BCryptPasswordEncoder();
        emailVerificationService = mock(EmailVerificationService.class);
        memberService = new MemberService(memberMapper, passwordEncoder, emailVerificationService);
    }

    private MemberSignupRequestDTO request(
            String loginId, String password, String confirm, LocalDate birthDate) {
        return new MemberSignupRequestDTO(
                loginId,
                " Hope@Example.com ",
                password,
                confirm,
                birthDate,
                "M",
                true,
                true,
                true,
                "v1.0",
                "v1.0",
                "v1.0",
                "verify-1");
    }

    private MemberSignupRequestDTO validRequest() {
        return request("Hope_User", "password123", "password123", LocalDate.of(2000, 1, 15));
    }

    // 정상 가입: 소문자 정리 · BCrypt 암호화 · 인증 확인 · 서버 지정 값 저장 · 인증 사용 처리
    @Test
    void signupSavesEncryptedMember() {

        given(memberMapper.countByLoginId("hope_user")).willReturn(0);
        given(memberMapper.countByEmail("hope@example.com")).willReturn(0);
        given(memberMapper.insertMember(any()))
                .willAnswer(
                        invocation -> {
                            invocation.<MemberInsertDTO>getArgument(0).setMemberId(7L);
                            return 1;
                        });

        MemberSignupResponseDTO response = memberService.signup(validRequest());

        ArgumentCaptor<MemberInsertDTO> captor = ArgumentCaptor.forClass(MemberInsertDTO.class);
        verify(memberMapper).insertMember(captor.capture());
        MemberInsertDTO saved = captor.getValue();

        assertThat(saved.getLoginId()).isEqualTo("hope_user");
        assertThat(saved.getEmail()).isEqualTo("hope@example.com");
        assertThat(saved.getPassword()).isNotEqualTo("password123").startsWith("$2");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
        assertThat(saved.getGender()).isEqualTo("M");
        assertThat(saved.getTermsVersion()).isEqualTo("v1.0");
        assertThat(saved.getEmailVerifiedYn()).isEqualTo("Y");
        assertThat(saved.getDataOrigin()).isEqualTo("INPUT");
        assertThat(response.memberId()).isEqualTo(7L);
        assertThat(response.loginId()).isEqualTo("hope_user");
        assertThat(response.email()).isEqualTo("hope@example.com");
        assertThat(response.name()).isNull();
        assertThat(response.role()).isEqualTo("USER");

        verify(emailVerificationService).requireVerified("verify-1", "hope@example.com");
        verify(emailVerificationService).complete("verify-1");
    }

    // 이메일 인증 미완료 → 저장 안 함
    @Test
    void unverifiedEmailThrows() {

        willThrow(
                        new BusinessException(
                                HttpStatus.BAD_REQUEST, "EMAIL_NOT_VERIFIED", "이메일 인증을 완료해 주세요"))
                .given(emailVerificationService)
                .requireVerified("verify-1", "hope@example.com");

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("EMAIL_NOT_VERIFIED");

        verify(memberMapper, never()).insertMember(any());
    }

    // 비밀번호 확인 불일치 → 400
    @Test
    void passwordMismatchThrows() {

        assertThatThrownBy(
                        () ->
                                memberService.signup(
                                        request(
                                                "hope_user",
                                                "password123",
                                                "password999",
                                                LocalDate.of(2000, 1, 15))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("비밀번호 확인이 일치하지 않아요");

        verify(memberMapper, never()).insertMember(any());
    }

    // 만 14세 미만 → UNDER_AGE
    @Test
    void underAgeThrows() {

        LocalDate thirteen = LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(13);

        assertThatThrownBy(
                        () ->
                                memberService.signup(
                                        request(
                                                "hope_user",
                                                "password123",
                                                "password123",
                                                thirteen)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("만 14세 이상만 가입할 수 있어요")
                .extracting("code")
                .isEqualTo("UNDER_AGE");
    }

    // 탈퇴 회원 형식 아이디 → INVALID_LOGIN_ID
    @Test
    void withdrawnPrefixLoginIdThrows() {

        assertThatThrownBy(
                        () ->
                                memberService.signup(
                                        request(
                                                "withdrawn_abc",
                                                "password123",
                                                "password123",
                                                LocalDate.of(2000, 1, 15))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용할 수 없는 아이디예요")
                .extracting("code")
                .isEqualTo("INVALID_LOGIN_ID");
    }

    // 중복 아이디 → DUPLICATE_LOGIN_ID
    @Test
    void duplicateLoginIdThrows() {

        given(memberMapper.countByLoginId("hope_user")).willReturn(1);

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 사용 중인 아이디예요")
                .extracting("code")
                .isEqualTo("DUPLICATE_LOGIN_ID");

        verify(memberMapper, never()).insertMember(any());
    }

    // 중복 이메일 (대소문자만 다른 경우 포함) → DUPLICATE_EMAIL
    @Test
    void duplicateEmailThrows() {

        given(memberMapper.countByLoginId("hope_user")).willReturn(0);
        given(memberMapper.countByEmail("hope@example.com")).willReturn(1);

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 가입된 이메일이에요")
                .extracting("code")
                .isEqualTo("DUPLICATE_EMAIL");
    }

    // 저장 중 키 충돌 → DUPLICATE_MEMBER, 인증은 사용 처리하지 않음
    @Test
    void duplicateKeyOnInsertThrows() {

        given(memberMapper.insertMember(any())).willThrow(new DuplicateKeyException("중복"));

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_MEMBER");

        verify(emailVerificationService, never()).complete(any());
    }

    // DB 저장 실패 (필수 칸 누락 등) → SIGNUP_FAILED 500
    @Test
    void dbFailureOnInsertThrows() {

        given(memberMapper.insertMember(any()))
                .willThrow(new DataIntegrityViolationException("ORA-01400"));

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("가입 처리 중 오류가 발생했습니다")
                .extracting("status")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // 아이디 중복확인: 대문자 입력도 소문자로 확인
    @Test
    void checkLoginIdNormalizesAndReportsDuplicate() {

        given(memberMapper.countByLoginId("test0001")).willReturn(1);

        MemberAvailabilityResponseDTO response = memberService.checkLoginId(" TEST0001 ");

        assertThat(response.available()).isFalse();
        assertThat(response.message()).isEqualTo("이미 사용 중인 아이디예요");
    }

    // 아이디 중복확인: withdrawn_ · 형식 오류 → 400
    @Test
    void checkLoginIdRejectsRestrictedOrInvalid() {

        assertThatThrownBy(() -> memberService.checkLoginId("withdrawn_x"))
                .extracting("code")
                .isEqualTo("INVALID_LOGIN_ID");
        assertThatThrownBy(() -> memberService.checkLoginId("ab"))
                .extracting("code")
                .isEqualTo("VALIDATION");
    }

    // 이메일 중복확인: 사용 가능 · 형식 오류
    @Test
    void checkEmailReportsAvailabilityAndFormat() {

        given(memberMapper.countByEmail("new@example.com")).willReturn(0);

        assertThat(memberService.checkEmail("New@Example.com").available()).isTrue();
        assertThatThrownBy(() -> memberService.checkEmail("not-an-email"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이메일 형식을 확인해 주세요");
    }
}
