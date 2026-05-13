package com.example.bootstrap.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.example.bootstrap.global.response.ApiResponse;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;

/**
 * GlobalExceptionHandler 단위 테스트.
 *
 * <p>비즈니스 예외, 유효성 검증 예외, 일반 예외 처리 및
 * LocaleContextHolder 기반 locale 동적 반영을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler 단위 테스트")
class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private WebExchangeBindException webExchangeBindException;

    @Mock
    private BindingResult bindingResult;

    private GlobalExceptionHandler handler;
    private MockServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(messageSource);
        exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test")
                .header("Accept-Language", "ko")
                .build()
        );
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ── BusinessException 처리 테스트 ──────────────────────────────────────────

    @Test
    @DisplayName("BusinessException AUTH_001 처리 — 401 UNAUTHORIZED 반환 + 응답 body 검증")
    void handleBusinessException_returnsCorrectHttpStatus() {
        when(messageSource.getMessage(
            eq(ErrorCode.AUTH_001.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("인증 토큰이 없거나 잘못된 형식입니다.");

        final BusinessException ex = new BusinessException(ErrorCode.AUTH_001);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTH_001");
        assertThat(response.getBody().message()).isEqualTo("인증 토큰이 없거나 잘못된 형식입니다.");
    }

    @Test
    @DisplayName("BusinessException AUTH_002(만료된 토큰) 처리 — 401 UNAUTHORIZED 반환")
    void handleBusinessException_expiredToken_returns401() {
        when(messageSource.getMessage(
            eq(ErrorCode.AUTH_002.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("만료된 토큰입니다.");

        final BusinessException ex = new BusinessException(ErrorCode.AUTH_002);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("AUTH_002");
    }

    @Test
    @DisplayName("BusinessException AUTH_003(블랙리스트 토큰) 처리 — 401 UNAUTHORIZED 반환")
    void handleBusinessException_blacklistedToken_returns401() {
        when(messageSource.getMessage(
            eq(ErrorCode.AUTH_003.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("블랙리스트에 등록된 토큰입니다.");

        final BusinessException ex = new BusinessException(ErrorCode.AUTH_003);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("AUTH_003");
    }

    @Test
    @DisplayName("BusinessException AUTH_004(잘못된 자격증명) 처리 — 401 UNAUTHORIZED 반환")
    void handleBusinessException_wrongCredentials_returns401() {
        when(messageSource.getMessage(
            eq(ErrorCode.AUTH_004.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("이메일 또는 비밀번호가 올바르지 않습니다.");

        final BusinessException ex = new BusinessException(ErrorCode.AUTH_004);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("AUTH_004");
    }

    @Test
    @DisplayName("BusinessException AUTH_006(지원하지 않는 OAuth2 공급자) 처리 — 400 BAD REQUEST 반환")
    void handleBusinessException_unsupportedOauth2Provider_returns400() {
        when(messageSource.getMessage(
            eq(ErrorCode.AUTH_006.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("지원하지 않는 OAuth2 공급자입니다.");

        final BusinessException ex = new BusinessException(ErrorCode.AUTH_006);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("AUTH_006");
    }

    @Test
    @DisplayName("BusinessException ACCOUNT_001(이메일 중복) 처리 — 409 CONFLICT 반환")
    void handleBusinessException_duplicateEmail_returns409() {
        when(messageSource.getMessage(
            eq(ErrorCode.ACCOUNT_001.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("이미 존재하는 이메일입니다.");

        final BusinessException ex = new BusinessException(ErrorCode.ACCOUNT_001);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_001");
    }

    @Test
    @DisplayName("BusinessException ACCOUNT_002(계정 없음) 처리 — 404 NOT FOUND 반환")
    void handleBusinessException_accountNotFound_returns404() {
        when(messageSource.getMessage(
            eq(ErrorCode.ACCOUNT_002.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("존재하지 않는 계정입니다.");

        final BusinessException ex = new BusinessException(ErrorCode.ACCOUNT_002);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_002");
    }

    @Test
    @DisplayName("BusinessException ACCOUNT_003(권한 없음) 처리 — 403 FORBIDDEN 반환")
    void handleBusinessException_forbidden_returns403() {
        when(messageSource.getMessage(
            eq(ErrorCode.ACCOUNT_003.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("권한이 없습니다.");

        final BusinessException ex = new BusinessException(ErrorCode.ACCOUNT_003);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_003");
    }

    @Test
    @DisplayName("BusinessException AI_001(AI 서비스 오류) 처리 — 500 INTERNAL SERVER ERROR 반환")
    void handleBusinessException_aiServiceError_returns500() {
        when(messageSource.getMessage(
            eq(ErrorCode.AI_001.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("AI 서비스 오류가 발생했습니다.");

        final BusinessException ex = new BusinessException(ErrorCode.AI_001);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("AI_001");
    }

    @Test
    @DisplayName("BusinessException AI_003(AI 타임아웃) 처리 — 504 GATEWAY TIMEOUT 반환")
    void handleBusinessException_aiTimeout_returns504() {
        when(messageSource.getMessage(
            eq(ErrorCode.AI_003.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("AI 모델 응답 시간이 초과되었습니다.");

        final BusinessException ex = new BusinessException(ErrorCode.AI_003);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().code()).isEqualTo("AI_003");
    }

    @Test
    @DisplayName("BusinessException COMMON_001(잘못된 입력값) 처리 — 400 BAD REQUEST 반환")
    void handleBusinessException_commonError_returns400() {
        when(messageSource.getMessage(
            eq(ErrorCode.COMMON_001.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("입력값이 유효하지 않습니다.");

        final BusinessException ex = new BusinessException(ErrorCode.COMMON_001);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("COMMON_001");
    }

    @Test
    @DisplayName("BusinessException COMMON_003(리소스 없음) 처리 — 404 NOT FOUND 반환")
    void handleBusinessException_resourceNotFound_returns404() {
        when(messageSource.getMessage(
            eq(ErrorCode.COMMON_003.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("요청한 리소스를 찾을 수 없습니다.");

        final BusinessException ex = new BusinessException(ErrorCode.COMMON_003);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("COMMON_003");
    }

    @Test
    @DisplayName("BusinessException — 커스텀 메시지 지정 시 MessageSource fallback으로 커스텀 메시지 사용")
    void handleBusinessException_withCustomMessage_usesFallbackMessage() {
        final String customMessage = "구체적인 에러 상세 내용";
        when(messageSource.getMessage(
            eq(ErrorCode.AUTH_001.getCode()), isNull(), eq(customMessage), any(Locale.class)))
            .thenReturn(customMessage);

        final BusinessException ex = new BusinessException(ErrorCode.AUTH_001, customMessage);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo(customMessage);
    }

    @Test
    @DisplayName("BusinessException 핸들링 완료 후 LocaleContextHolder 초기화 — 스레드 오염 방지")
    void handleBusinessException_resetsLocaleContextHolderAfterHandling() {
        when(messageSource.getMessage(
            eq(ErrorCode.AUTH_001.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("인증 오류");

        handler.handleBusinessException(new BusinessException(ErrorCode.AUTH_001), exchange);

        assertThat(LocaleContextHolder.getLocaleContext()).isNull();
    }

    @Test
    @DisplayName("Accept-Language 영어 요청 — MessageSource에 영어 locale 전달")
    void handleBusinessException_withEnglishLocale_passesEnglishLocaleToMessageSource() {
        final MockServerWebExchange englishExchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test")
                .header("Accept-Language", "en")
                .build()
        );
        when(messageSource.getMessage(
            eq(ErrorCode.AUTH_001.getCode()), isNull(), anyString(), any(Locale.class)))
            .thenReturn("Authentication token is missing or invalid.");

        final BusinessException ex = new BusinessException(ErrorCode.AUTH_001);
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleBusinessException(ex, englishExchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Authentication token is missing or invalid.");
    }

    // ── WebExchangeBindException (유효성 검증) 처리 테스트 ───────────────────────

    @Test
    @DisplayName("WebExchangeBindException — 단일 필드 에러 시 400 + FieldError 목록 반환")
    void handleValidationException_withSingleFieldError_returnsFieldError() {
        final List<FieldError> fieldErrors = List.of(
            new FieldError("registerRequest", "email", "이메일은 필수입니다.")
        );
        when(webExchangeBindException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);
        when(messageSource.getMessage(
            eq("COMMON_001"), isNull(), anyString(), any(Locale.class)))
            .thenReturn("입력값이 유효하지 않습니다.");

        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleValidationException(webExchangeBindException, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COMMON_001");
        assertThat(response.getBody().message()).isEqualTo("입력값이 유효하지 않습니다.");
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("email");
        assertThat(response.getBody().errors().get(0).reason()).isEqualTo("이메일은 필수입니다.");
    }

    @Test
    @DisplayName("WebExchangeBindException — 복수 필드 에러 시 모든 에러 목록 순서대로 반환")
    void handleValidationException_withMultipleFieldErrors_returnsAllErrors() {
        final List<FieldError> fieldErrors = List.of(
            new FieldError("registerRequest", "email", "이메일은 필수입니다."),
            new FieldError("registerRequest", "password", "비밀번호는 필수입니다."),
            new FieldError("registerRequest", "nickname", "닉네임은 필수입니다.")
        );
        when(webExchangeBindException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);
        when(messageSource.getMessage(
            eq("COMMON_001"), isNull(), anyString(), any(Locale.class)))
            .thenReturn("입력값이 유효하지 않습니다.");

        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleValidationException(webExchangeBindException, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errors()).hasSize(3);
        assertThat(response.getBody().errors())
            .extracting(ApiResponse.FieldError::field)
            .containsExactly("email", "password", "nickname");
        assertThat(response.getBody().errors())
            .extracting(ApiResponse.FieldError::reason)
            .containsExactly("이메일은 필수입니다.", "비밀번호는 필수입니다.", "닉네임은 필수입니다.");
    }

    @Test
    @DisplayName("WebExchangeBindException — 빈 필드 에러 목록 시 errors 빈 리스트 반환")
    void handleValidationException_withNoFieldErrors_returnsEmptyErrors() {
        when(webExchangeBindException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        when(messageSource.getMessage(
            eq("COMMON_001"), isNull(), anyString(), any(Locale.class)))
            .thenReturn("입력값이 유효하지 않습니다.");

        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleValidationException(webExchangeBindException, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    @DisplayName("WebExchangeBindException 핸들링 완료 후 LocaleContextHolder 초기화 — 스레드 오염 방지")
    void handleValidationException_resetsLocaleContextAfterHandling() {
        when(webExchangeBindException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        when(messageSource.getMessage(
            eq("COMMON_001"), isNull(), anyString(), any(Locale.class)))
            .thenReturn("입력값이 유효하지 않습니다.");

        handler.handleValidationException(webExchangeBindException, exchange);

        assertThat(LocaleContextHolder.getLocaleContext()).isNull();
    }

    // ── 일반 Exception 처리 테스트 ─────────────────────────────────────────────

    @Test
    @DisplayName("일반 Exception 처리 — 500 INTERNAL SERVER ERROR 반환")
    void handleException_returnsInternalServerError() {
        when(messageSource.getMessage(
            eq("COMMON_002"), isNull(), anyString(), any(Locale.class)))
            .thenReturn("서버 내부 오류가 발생했습니다.");

        final Exception ex = new RuntimeException("unexpected error");
        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COMMON_002");
        assertThat(response.getBody().message()).isEqualTo("서버 내부 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("일반 Exception — data/errors 필드는 null 반환 (Envelope 최소화)")
    void handleException_hasNullDataAndErrors() {
        when(messageSource.getMessage(
            eq("COMMON_002"), isNull(), anyString(), any(Locale.class)))
            .thenReturn("서버 오류");

        final ResponseEntity<ApiResponse<Void>> response =
            handler.handleException(new RuntimeException("err"), exchange);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().errors()).isNull();
    }

    @Test
    @DisplayName("Exception 핸들링 완료 후 LocaleContextHolder 초기화 — 스레드 오염 방지")
    void handleException_resetsLocaleContextHolderAfterHandling() {
        when(messageSource.getMessage(
            eq("COMMON_002"), isNull(), anyString(), any(Locale.class)))
            .thenReturn("서버 오류");

        handler.handleException(new RuntimeException("error"), exchange);

        assertThat(LocaleContextHolder.getLocaleContext()).isNull();
    }
}
