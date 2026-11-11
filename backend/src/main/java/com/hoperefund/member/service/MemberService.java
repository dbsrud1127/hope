package com.hoperefund.member.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.dto.MemberAvailabilityResponseDTO;
import com.hoperefund.member.dto.MemberInsertDTO;
import com.hoperefund.member.dto.MemberSignupRequestDTO;
import com.hoperefund.member.dto.MemberSignupResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 처리 Service (F-01-01)
 * 아이디 검사 → 이메일 검사 → 만 14세 확인 → 비밀번호 검사 → 이메일 인증 확인
 * → BCrypt 암호화 → MEMBER 저장 → 인증 사용 처리
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private static final int MIN_SIGNUP_AGE = 14; // 가입 가능 최소 나이 (만)
    private static final int BCRYPT_MAX_BYTES = 72; // BCrypt 입력 한도
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private static final String EMAIL_VERIFIED = "Y"; // 인증을 마친 경우에만 가입되므로 항상 Y
    private static final String ROLE_USER = "USER"; // MEMBER.ROLE 기본값 (INSERT에서 뺌)
    // TODO: MEMBER.DATA_ORIGIN 허용 값 팀 확인 필요 (화면 입력 데이터 = INPUT으로 임시 사용)
    private static final String DATA_ORIGIN_INPUT = "INPUT";

    // 중복확인 API용 형식 검사 (가입 요청은 MemberSignupRequestDTO의 @Valid가 같은 규칙으로 검사)
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-z0-9_]{4,50}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public MemberService(
            MemberMapper memberMapper,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public MemberSignupResponseDTO signup(MemberSignupRequestDTO request) {

        String loginId = normalize(request.loginId());
        String email = normalize(request.email());

        // 아이디: 사용 제한 → 중복 (가상 회원 test0001~ · admin · 탈퇴 회원 모두 대상)
        validateLoginIdNotRestricted(loginId);
        if (memberMapper.countByLoginId(loginId) > 0) {
            throw duplicateLoginId();
        }
        // 이메일: 소문자로 바꾼 값으로 중복 확인
        if (memberMapper.countByEmail(email) > 0) {
            throw duplicateEmail();
        }
        validateBirthDate(request.birthDate());
        validatePassword(request.password(), request.passwordConfirm());

        // 이메일 인증을 같은 이메일로 마쳤는지 서버에서 다시 확인
        emailVerificationService.requireVerified(request.verificationId(), email);

        MemberInsertDTO member = new MemberInsertDTO();
        member.setLoginId(loginId);
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(request.password())); // BCrypt 암호문만 저장
        member.setBirthDate(request.birthDate());
        member.setGender(request.gender());
        member.setTermsVersion(request.termsVersion().trim());
        member.setPrivacyVersion(request.privacyVersion().trim());
        member.setFinanceVersion(request.financeVersion().trim());
        member.setEmailVerifiedYn(EMAIL_VERIFIED);
        member.setDataOrigin(DATA_ORIGIN_INPUT);

        try {
            memberMapper.insertMember(member);
        } catch (DuplicateKeyException e) {
            // 동시에 가입 요청이 들어와 번호·아이디가 겹친 경우
            throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_MEMBER", "잠시 후 다시 시도해 주세요");
        } catch (DataAccessException e) {
            // DB 저장 실패 (필수 칸 누락 ORA-01400 · 제약조건 위반 등): 원인은 로그로만
            log.error("회원 저장 실패: {}", e.getClass().getSimpleName(), e);
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "SIGNUP_FAILED", "가입 처리 중 오류가 발생했습니다");
        }

        // 같은 인증으로 다시 가입하지 못하게 사용 처리
        emailVerificationService.complete(request.verificationId());

        // name: 실명 미수집 · createdAt: DB 기본값(SYSDATE)과 같은 시점의 서버 시각
        return new MemberSignupResponseDTO(
                member.getMemberId(), loginId, email, null, ROLE_USER, LocalDateTime.now(KOREA));
    }

    // 아이디 중복확인: 형식 오류 · 사용 제한은 400, 중복이면 available=false
    public MemberAvailabilityResponseDTO checkLoginId(String rawLoginId) {

        String loginId = normalize(rawLoginId);
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VALIDATION", "아이디는 영문·숫자·밑줄 4~50자로 입력해 주세요");
        }
        validateLoginIdNotRestricted(loginId);

        if (memberMapper.countByLoginId(loginId) > 0) {
            return new MemberAvailabilityResponseDTO(false, "이미 사용 중인 아이디예요");
        }
        return new MemberAvailabilityResponseDTO(true, "사용 가능한 아이디예요");
    }

    // 이메일 중복확인: 형식 오류는 400, 중복이면 available=false
    public MemberAvailabilityResponseDTO checkEmail(String rawEmail) {

        String email = normalize(rawEmail);
        if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION", "이메일 형식을 확인해 주세요");
        }

        if (memberMapper.countByEmail(email) > 0) {
            return new MemberAvailabilityResponseDTO(false, "이미 가입된 이메일이에요");
        }
        return new MemberAvailabilityResponseDTO(true, "사용 가능한 이메일이에요");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    // 탈퇴 회원 아이디 형식(withdrawn_)과 겹치지 않게 막음
    private static void validateLoginIdNotRestricted(String loginId) {
        if (loginId.startsWith("withdrawn_")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_LOGIN_ID", "사용할 수 없는 아이디예요");
        }
    }

    private static BusinessException duplicateLoginId() {
        return new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디예요");
    }

    private static BusinessException duplicateEmail() {
        return new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일이에요");
    }

    // 비밀번호 확인 일치 · BCrypt 바이트 한도 검사
    private static void validatePassword(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION", "비밀번호 확인이 일치하지 않아요");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION", "비밀번호가 너무 길어요");
        }
    }

    // 만 14세 이상만 가입 (한국 시간 기준)
    private static void validateBirthDate(LocalDate birthDate) {
        LocalDate today = LocalDate.now(KOREA);
        if (birthDate.isAfter(today)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION", "생년월일을 확인해 주세요");
        }
        if (Period.between(birthDate, today).getYears() < MIN_SIGNUP_AGE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "UNDER_AGE", "만 14세 이상만 가입할 수 있어요");
        }
    }
}
