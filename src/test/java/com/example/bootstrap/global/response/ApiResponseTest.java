package com.example.bootstrap.global.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bootstrap.global.response.ApiResponse.FieldError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ApiResponse} 공통 응답 래퍼 단위 테스트.
 *
 * <p>성공·실패 응답 직렬화 구조, 상태 코드 포함 여부,
 * JSON 필드 일관성, 방어적 복사 동작을 검증합니다.
 */
@DisplayName("ApiResponse 공통 응답 래퍼 단위 테스트")
class ApiResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ── success(message) ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("success(message) — 데이터 없는 성공 응답")
    class SuccessWithoutData {

        @Test
        @DisplayName("code 필드가 'SUCCESS'이다")
        void success_withoutData_hasSuccessCode() {
            final ApiResponse<Void> response = ApiResponse.success("처리 완료");

            assertThat(response.code()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("message 필드가 전달된 메시지와 동일하다")
        void success_withoutData_hasProvidedMessage() {
            final String message = "처리 완료";
            final ApiResponse<Void> response = ApiResponse.success(message);

            assertThat(response.message()).isEqualTo(message);
        }

        @Test
        @DisplayName("data 필드가 null이다")
        void success_withoutData_hasNullData() {
            final ApiResponse<Void> response = ApiResponse.success("처리 완료");

            assertThat(response.data()).isNull();
        }

        @Test
        @DisplayName("errors 필드가 null이다")
        void success_withoutData_hasNullErrors() {
            final ApiResponse<Void> response = ApiResponse.success("처리 완료");

            assertThat(response.errors()).isNull();
        }

        @Test
        @DisplayName("JSON 직렬화 시 null 필드(data, errors)가 제외된다 — @JsonInclude(NON_NULL)")
        void success_withoutData_serializedJson_excludesNullFields() throws Exception {
            final ApiResponse<Void> response = ApiResponse.success("처리 완료");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.has("code")).isTrue();
            assertThat(json.has("message")).isTrue();
            assertThat(json.has("data")).isFalse();
            assertThat(json.has("errors")).isFalse();
        }

        @Test
        @DisplayName("JSON 직렬화 시 code 값이 'SUCCESS'이다")
        void success_withoutData_serializedJson_codeIsSuccess() throws Exception {
            final ApiResponse<Void> response = ApiResponse.success("OK");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.get("code").asText()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("JSON 직렬화 시 message 값이 올바르게 포함된다")
        void success_withoutData_serializedJson_messageIsCorrect() throws Exception {
            final ApiResponse<Void> response = ApiResponse.success("작업 성공");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.get("message").asText()).isEqualTo("작업 성공");
        }
    }

    // ── success(message, data) ───────────────────────────────────────────────

    @Nested
    @DisplayName("success(message, data) — 데이터 있는 성공 응답")
    class SuccessWithData {

        @Test
        @DisplayName("code 필드가 'SUCCESS'이다")
        void success_withData_hasSuccessCode() {
            final ApiResponse<String> response = ApiResponse.success("조회 성공", "result");

            assertThat(response.code()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("data 필드가 전달된 객체와 동일하다")
        void success_withData_hasProvidedData() {
            final String data = "result-value";
            final ApiResponse<String> response = ApiResponse.success("조회 성공", data);

            assertThat(response.data()).isEqualTo(data);
        }

        @Test
        @DisplayName("errors 필드가 null이다")
        void success_withData_hasNullErrors() {
            final ApiResponse<String> response = ApiResponse.success("조회 성공", "data");

            assertThat(response.errors()).isNull();
        }

        @Test
        @DisplayName("JSON 직렬화 시 code, message, data 필드가 모두 포함된다")
        void success_withData_serializedJson_containsAllThreeFields() throws Exception {
            final ApiResponse<String> response = ApiResponse.success("조회 성공", "hello");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.has("code")).isTrue();
            assertThat(json.has("message")).isTrue();
            assertThat(json.has("data")).isTrue();
        }

        @Test
        @DisplayName("JSON 직렬화 시 data 값이 올바르게 포함된다")
        void success_withData_serializedJson_dataValueIsCorrect() throws Exception {
            final ApiResponse<String> response = ApiResponse.success("조회 성공", "my-data");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.get("data").asText()).isEqualTo("my-data");
        }

        @Test
        @DisplayName("JSON 직렬화 시 errors 필드가 null이라 제외된다")
        void success_withData_serializedJson_excludesNullErrors() throws Exception {
            final ApiResponse<String> response = ApiResponse.success("조회 성공", "data");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.has("errors")).isFalse();
        }

        @Test
        @DisplayName("레코드 타입 data도 JSON 중첩 객체로 올바르게 직렬화된다")
        void success_withRecordData_serializedJson_containsNestedObject() throws Exception {
            record UserInfo(String name, int age) { }

            final ApiResponse<UserInfo> response = ApiResponse.success("조회 성공", new UserInfo("김철수", 30));
            final JsonNode json = objectMapper.valueToTree(response);
            final JsonNode dataNode = json.get("data");

            assertThat(dataNode).isNotNull();
            assertThat(dataNode.get("name").asText()).isEqualTo("김철수");
            assertThat(dataNode.get("age").asInt()).isEqualTo(30);
        }
    }

    // ── error(code, message) ─────────────────────────────────────────────────

    @Nested
    @DisplayName("error(code, message) — 에러 응답")
    class ErrorResponse {

        @Test
        @DisplayName("code 필드가 전달된 에러 코드와 동일하다")
        void error_hasProvidedErrorCode() {
            final ApiResponse<Void> response = ApiResponse.error("AUTH_001", "인증 오류");

            assertThat(response.code()).isEqualTo("AUTH_001");
        }

        @Test
        @DisplayName("message 필드가 전달된 메시지와 동일하다")
        void error_hasProvidedMessage() {
            final String message = "인증 토큰이 없거나 잘못된 형식입니다.";
            final ApiResponse<Void> response = ApiResponse.error("AUTH_001", message);

            assertThat(response.message()).isEqualTo(message);
        }

        @Test
        @DisplayName("data 필드가 null이다")
        void error_hasNullData() {
            final ApiResponse<Void> response = ApiResponse.error("AUTH_001", "에러");

            assertThat(response.data()).isNull();
        }

        @Test
        @DisplayName("errors 필드가 null이다")
        void error_hasNullErrors() {
            final ApiResponse<Void> response = ApiResponse.error("AUTH_001", "에러");

            assertThat(response.errors()).isNull();
        }

        @Test
        @DisplayName("JSON 직렬화 시 code와 message만 포함되고 data·errors는 제외된다")
        void error_serializedJson_onlyCodeAndMessagePresent() throws Exception {
            final ApiResponse<Void> response = ApiResponse.error("ACCOUNT_002", "존재하지 않는 계정");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.has("code")).isTrue();
            assertThat(json.has("message")).isTrue();
            assertThat(json.has("data")).isFalse();
            assertThat(json.has("errors")).isFalse();
        }

        @Test
        @DisplayName("JSON 직렬화 시 code 값이 도메인 접두사를 포함한 에러 코드이다")
        void error_serializedJson_codeHasDomainPrefix() throws Exception {
            final ApiResponse<Void> response = ApiResponse.error("AI_001", "AI 서비스 오류");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.get("code").asText()).isEqualTo("AI_001");
        }

        @Test
        @DisplayName("다양한 도메인 에러 코드(AUTH/ACCOUNT/AI/BATCH/COMMON)가 그대로 유지된다")
        void error_withVariousDomainCodes_preservesCodeValue() {
            final String[] errorCodes = {
                "AUTH_001", "ACCOUNT_001", "AI_001", "BATCH_001", "COMMON_001"
            };

            for (final String code : errorCodes) {
                final ApiResponse<Void> response = ApiResponse.error(code, "message");
                assertThat(response.code()).isEqualTo(code);
            }
        }
    }

    // ── validationError(code, message, errors) ────────────────────────────────

    @Nested
    @DisplayName("validationError(code, message, errors) — 유효성 검증 에러 응답")
    class ValidationErrorResponse {

        @Test
        @DisplayName("code 필드가 전달된 코드와 동일하다")
        void validationError_hasProvidedCode() {
            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "유효성 검증 실패",
                List.of(new FieldError("email", "이메일 형식 오류"))
            );

            assertThat(response.code()).isEqualTo("COMMON_001");
        }

        @Test
        @DisplayName("data 필드가 null이다")
        void validationError_hasNullData() {
            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "유효성 검증 실패",
                List.of(new FieldError("email", "이메일 형식 오류"))
            );

            assertThat(response.data()).isNull();
        }

        @Test
        @DisplayName("errors 필드가 전달된 FieldError 목록과 동일하다")
        void validationError_hasProvidedErrors() {
            final List<FieldError> errors = List.of(
                new FieldError("email", "이메일은 필수입니다."),
                new FieldError("password", "비밀번호는 8자 이상이어야 합니다.")
            );
            final ApiResponse<Void> response = ApiResponse.validationError("COMMON_001", "입력 오류", errors);

            assertThat(response.errors()).hasSize(2);
            assertThat(response.errors().get(0).field()).isEqualTo("email");
            assertThat(response.errors().get(0).reason()).isEqualTo("이메일은 필수입니다.");
            assertThat(response.errors().get(1).field()).isEqualTo("password");
        }

        @Test
        @DisplayName("빈 errors 목록도 정상적으로 처리된다")
        void validationError_withEmptyErrors_hasEmptyErrorsList() {
            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "유효성 검증 실패", List.of()
            );

            assertThat(response.errors()).isEmpty();
        }

        @Test
        @DisplayName("JSON 직렬화 시 errors 배열이 포함된다")
        void validationError_serializedJson_containsErrorsArray() throws Exception {
            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "유효성 검증 실패",
                List.of(new FieldError("email", "이메일은 필수입니다."))
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.has("errors")).isTrue();
            assertThat(json.get("errors").isArray()).isTrue();
            assertThat(json.get("errors").size()).isEqualTo(1);
        }

        @Test
        @DisplayName("JSON 직렬화 시 errors 배열의 각 항목이 field·reason 필드를 포함한다")
        void validationError_serializedJson_errorsItemHasFieldAndReason() throws Exception {
            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "유효성 검증 실패",
                List.of(new FieldError("nickname", "닉네임은 필수입니다."))
            );
            final JsonNode json = objectMapper.valueToTree(response);
            final JsonNode firstError = json.get("errors").get(0);

            assertThat(firstError.has("field")).isTrue();
            assertThat(firstError.has("reason")).isTrue();
            assertThat(firstError.get("field").asText()).isEqualTo("nickname");
            assertThat(firstError.get("reason").asText()).isEqualTo("닉네임은 필수입니다.");
        }

        @Test
        @DisplayName("JSON 직렬화 시 data 필드는 null이라 제외된다")
        void validationError_serializedJson_excludesNullData() throws Exception {
            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "유효성 검증 실패",
                List.of(new FieldError("email", "필수"))
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.has("data")).isFalse();
        }

        @Test
        @DisplayName("복수 errors 항목이 JSON 배열에 순서대로 포함된다")
        void validationError_serializedJson_multipleErrorsInOrder() throws Exception {
            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "유효성 검증 실패",
                List.of(
                    new FieldError("email", "이메일 오류"),
                    new FieldError("password", "비밀번호 오류"),
                    new FieldError("nickname", "닉네임 오류")
                )
            );
            final JsonNode json = objectMapper.valueToTree(response);
            final JsonNode errors = json.get("errors");

            assertThat(errors.size()).isEqualTo(3);
            assertThat(errors.get(0).get("field").asText()).isEqualTo("email");
            assertThat(errors.get(1).get("field").asText()).isEqualTo("password");
            assertThat(errors.get(2).get("field").asText()).isEqualTo("nickname");
        }
    }

    // ── FieldError 레코드 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("FieldError 내부 레코드")
    class FieldErrorRecord {

        @Test
        @DisplayName("field와 reason 값이 생성자에서 전달한 값과 동일하다")
        void fieldError_hasCorrectFieldAndReason() {
            final FieldError fieldError = new FieldError("email", "이메일 형식이 올바르지 않습니다.");

            assertThat(fieldError.field()).isEqualTo("email");
            assertThat(fieldError.reason()).isEqualTo("이메일 형식이 올바르지 않습니다.");
        }

        @Test
        @DisplayName("동일한 field·reason을 가진 두 FieldError는 equals로 동일하다")
        void fieldError_withSameValues_isEqual() {
            final FieldError a = new FieldError("password", "8자 이상");
            final FieldError b = new FieldError("password", "8자 이상");

            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("다른 field를 가진 두 FieldError는 equals로 다르다")
        void fieldError_withDifferentField_isNotEqual() {
            final FieldError a = new FieldError("email", "필수");
            final FieldError b = new FieldError("password", "필수");

            assertThat(a).isNotEqualTo(b);
        }
    }

    // ── 방어적 복사 (Defensive Copy) ────────────────────────────────────────────

    @Nested
    @DisplayName("방어적 복사 — errors 리스트 불변성")
    class DefensiveCopy {

        @Test
        @DisplayName("생성자에 전달한 가변 리스트를 수정해도 응답의 errors에 영향이 없다")
        void errors_defensivelyCopied_originalMutationHasNoEffect() {
            final List<FieldError> mutableErrors = new ArrayList<>();
            mutableErrors.add(new FieldError("email", "이메일 오류"));

            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "오류", mutableErrors
            );
            mutableErrors.add(new FieldError("password", "추가된 오류"));

            assertThat(response.errors()).hasSize(1);
            assertThat(response.errors().get(0).field()).isEqualTo("email");
        }

        @Test
        @DisplayName("응답의 errors 리스트는 수정 불가능하다 (UnsupportedOperationException)")
        void errors_returnedList_isImmutable() {
            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "오류",
                List.of(new FieldError("email", "필수"))
            );

            assertThatThrownBy(() -> response.errors().add(new FieldError("password", "추가")))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("errors가 null인 경우 응답의 errors 필드도 null이다")
        void errors_whenNullProvided_staysNull() {
            final ApiResponse<Void> response = new ApiResponse<>("SUCCESS", "OK", null, null);

            assertThat(response.errors()).isNull();
        }
    }

    // ── Envelope 구조 일관성 (JSON 필드 4개) ──────────────────────────────────

    @Nested
    @DisplayName("Envelope 응답 구조 일관성 — {code, message, data, errors}")
    class EnvelopeStructureConsistency {

        @Test
        @DisplayName("성공(데이터 포함) 응답의 JSON은 code·message·data 3개 필드를 가진다")
        void success_withData_jsonHasThreeFields() throws Exception {
            final ApiResponse<String> response = ApiResponse.success("성공", "value");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("성공(데이터 없음) 응답의 JSON은 code·message 2개 필드만 가진다")
        void success_withoutData_jsonHasTwoFields() throws Exception {
            final ApiResponse<Void> response = ApiResponse.success("성공");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("에러 응답의 JSON은 code·message 2개 필드만 가진다")
        void error_jsonHasTwoFields() throws Exception {
            final ApiResponse<Void> response = ApiResponse.error("AUTH_001", "인증 오류");
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("유효성 검증 에러 응답의 JSON은 code·message·errors 3개 필드를 가진다")
        void validationError_jsonHasThreeFields() throws Exception {
            final ApiResponse<Void> response = ApiResponse.validationError(
                "COMMON_001", "유효성 오류",
                List.of(new FieldError("email", "필수"))
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("JSON 역직렬화 후 원본 ApiResponse와 동일한 값을 가진다")
        void success_withData_roundTrip_preservesValues() throws Exception {
            final ApiResponse<String> original = ApiResponse.success("조회 성공", "test-data");
            final String jsonString = objectMapper.writeValueAsString(original);
            final JsonNode parsed = objectMapper.readTree(jsonString);

            assertThat(parsed.get("code").asText()).isEqualTo("SUCCESS");
            assertThat(parsed.get("message").asText()).isEqualTo("조회 성공");
            assertThat(parsed.get("data").asText()).isEqualTo("test-data");
        }
    }
}
