package com.hoperefund.member.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.dto.EmailVerificationResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 회원가입 이메일 인증 Service
 * 인증번호 발송 → 인증번호 확인 → (회원가입 시) 인증 완료 확인 · 사용 처리
 *
 * 보관 위치: ERD에 가입 전 인증 테이블이 없어 서버 메모리에 보관한다 (decisions/0004).
 * - 인증번호는 원문이 아니라 BCrypt 해시로만 보관하고, 로그에 남기지 않는다.
 * - 서버를 재시작하면 진행 중인 인증은 사라진다 (다시 인증하면 됨).
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    static final Duration CODE_VALID = Duration.ofMinutes(5); // 인증번호 유효 시간
    static final Duration VERIFIED_VALID = Duration.ofMinutes(30); // 인증 완료 후 가입까지 허용 시간
    static final int MAX_FAIL_COUNT = 5; // 인증번호 확인 최대 실패 횟수

    private static final String FAIL_MESSAGE = "인증 코드가 맞지 않거나 만료됐어요";

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final boolean devCodeEnabled;
    private final SecureRandom random = new SecureRandom();

    // 인증 번호표(verificationId) → 인증 진행 상태
    private final Map<String, Verification> verifications = new ConcurrentHashMap<>();

    @Autowired
    public EmailVerificationService(
            MemberMapper memberMapper,
            PasswordEncoder passwordEncoder,
            @Value("${member.email-verification.dev-code-enabled:false}") boolean devCodeEnabled) {
        this(memberMapper, passwordEncoder, Clock.systemUTC(), devCodeEnabled);
    }

    // 테스트에서 시각을 바꿔 만료를 확인할 수 있도록 Clock을 받는 생성자
    EmailVerificationService(
            MemberMapper memberMapper,
            PasswordEncoder passwordEncoder,
            Clock clock,
            boolean devCodeEnabled) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.devCodeEnabled = devCodeEnabled;
    }

    // 인증번호 발송: 이미 가입된 이메일이면 보내지 않는다
    public EmailVerificationResponseDTO send(String rawEmail) {

        String email = normalize(rawEmail);
        if (memberMapper.countByEmail(email) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일이에요");
        }

        removeExpired();

        String code = String.format("%06d", random.nextInt(1_000_000)); // 000000~999999
        String verificationId = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plus(CODE_VALID);

        verifications.put(
                verificationId, new Verification(email, passwordEncoder.encode(code), expiresAt));

        // TODO: 메일 발송 구현 (spring-boot-starter-mail · SMTP 계정 확정 후). 지금은 메일을 보내지 않는다.
        log.info("이메일 인증번호 발급 (메일 발송 미구현)");

        return new EmailVerificationResponseDTO(
                verificationId, expiresAt, devCodeEnabled ? code : null);
    }

    // 인증번호 확인: 맞으면 인증 완료로 표시
    public void confirm(String verificationId, String code) {

        Verification v = verifications.get(verificationId);
        Instant now = clock.instant();

        if (v == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VERIFICATION_FAILED", FAIL_MESSAGE);
        }
        synchronized (v) {
            if (v.verified) {
                return; // 이미 인증 완료 (중복 클릭)
            }
            if (!now.isBefore(v.expiresAt)) {
                verifications.remove(verificationId);
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "VERIFICATION_EXPIRED", FAIL_MESSAGE);
            }
            if (!passwordEncoder.matches(code, v.codeHash)) {
                v.failCount++;
                if (v.failCount >= MAX_FAIL_COUNT) {
                    verifications.remove(verificationId);
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST,
                            "VERIFICATION_LIMIT",
                            "인증 코드를 5번 틀렸어요. 인증번호를 다시 받아 주세요");
                }
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "VERIFICATION_FAILED", FAIL_MESSAGE);
            }
            v.verified = true;
            v.verifiedUntil = now.plus(VERIFIED_VALID);
        }
    }

    // 회원가입 전: 이 번호표가 같은 이메일로 인증을 마쳤는지 확인 (아니면 400)
    public void requireVerified(String verificationId, String email) {

        Verification v = verifications.get(verificationId);
        Instant now = clock.instant();

        if (v == null || !v.verified || !v.email.equals(email) || !now.isBefore(v.verifiedUntil)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "EMAIL_NOT_VERIFIED", "이메일 인증을 완료해 주세요");
        }
    }

    // 회원가입 저장 후: 같은 인증으로 다시 가입하지 못하게 지운다
    public void complete(String verificationId) {
        verifications.remove(verificationId);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // 오래된 인증 기록을 지워 메모리가 계속 늘지 않게 한다
    private void removeExpired() {
        Instant now = clock.instant();
        verifications
                .entrySet()
                .removeIf(
                        e -> {
                            Verification v = e.getValue();
                            Instant end = v.verified ? v.verifiedUntil : v.expiresAt;
                            return !now.isBefore(end);
                        });
    }

    // 인증 진행 상태 (메모리 보관용)
    private static final class Verification {
        private final String email; // 소문자 이메일
        private final String codeHash; // 인증번호 BCrypt 해시
        private final Instant expiresAt; // 인증번호 만료 시각
        private int failCount;
        private boolean verified;
        private Instant verifiedUntil;

        private Verification(String email, String codeHash, Instant expiresAt) {
            this.email = email;
            this.codeHash = codeHash;
            this.expiresAt = expiresAt;
        }
    }
}
