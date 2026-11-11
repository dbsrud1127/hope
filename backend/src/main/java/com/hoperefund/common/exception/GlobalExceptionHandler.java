package com.hoperefund.common.exception;

import com.hoperefund.common.dto.ErrorResponseDTO;
import jakarta.validation.ConstraintViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 공통 API 예외 처리 클래스
 * 팀 API 규칙에 맞춰 오류 응답 형식을 통일한다.
 * TODO: 인증·권한·업무 오류 등 오류 코드별 처리 추가 필요
 */
@RestControllerAdvice // 여러 Controller에서 발생한 예외를 공통 처리
public class GlobalExceptionHandler {

    private static final String DEFAULT_VALIDATION_MESSAGE = "입력값을 확인해 주세요.";

    // 요청 데이터 유효성 검증 오류 @ExceptionHandler - 어떤 종류의 예외를 처리할지 지정
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO>
            handleValidation( // ResponseEntity - HTTP 상태 코드와 응답 본문을 함께 반환
            MethodArgumentNotValidException e) {

        ErrorResponseDTO response = new ErrorResponseDTO("VALIDATION", validationMessage(e));

        return ResponseEntity // HTTP 상태 코드와 응답 본문을 함께 반환
                .status(HttpStatus.BAD_REQUEST) // 400 입력 오류
                .body(response);
    }

    // DTO에 message = "..."로 직접 적은 문구가 있으면 그 문구를, 없으면(기본 영어·라이브러리 문구) 공통 문구를 쓴다
    private static String validationMessage(MethodArgumentNotValidException e) {

        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError == null || !fieldError.contains(ConstraintViolation.class)) {
            return DEFAULT_VALIDATION_MESSAGE;
        }
        String template = fieldError.unwrap(ConstraintViolation.class).getMessageTemplate();
        if (template == null || template.startsWith("{")) {
            return DEFAULT_VALIDATION_MESSAGE; // 라이브러리 기본 문구 템플릿
        }
        return fieldError.getDefaultMessage();
    }

    // JSON 형식 오류
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidJson(HttpMessageNotReadableException e) {

        ErrorResponseDTO response = new ErrorResponseDTO("VALIDATION", "요청 데이터 형식을 확인해 주세요.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 업무 규칙 위반 (중복 아이디 등) - 서비스에서 정한 상태 코드·오류 코드·문구로 응답
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusiness(BusinessException e) {

        // 원인은 오류 코드만 로그로 남김 (개인정보 제외)
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .warn("업무 오류 발생: {}", e.getCode());

        ErrorResponseDTO response = new ErrorResponseDTO(e.getCode(), e.getMessage());

        return ResponseEntity.status(e.getStatus()).body(response);
    }

    // 존재하지 않는 정적 리소스 또는 API 경로
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(NoResourceFoundException e) {

        ErrorResponseDTO response = new ErrorResponseDTO("NOT_FOUND", "요청한 주소를 찾을 수 없어요.");

        return ResponseEntity.status(HttpStatus.NOT_FOUND) // 404 조회 주소 없음
                .body(response);
    }

    // 처리하지 못한 서버 오류
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleException(Exception e) {

        // 민감정보가 포함될 가능성이 있는 상세 예외 메시지는 출력하지 않음
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("서버 오류 발생: {}", e.getClass().getSimpleName(), e);

        ErrorResponseDTO response = new ErrorResponseDTO("SERVER_ERROR", "잠시 후 다시 시도해 주세요.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // 500 서버 오류
                .body(response);
    }
}
