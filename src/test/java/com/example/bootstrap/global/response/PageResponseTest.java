package com.example.bootstrap.global.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PageResponse} 페이지네이션 응답 래퍼 단위 테스트.
 *
 * <p>총 페이지 수 계산, 필드 일관성, JSON 직렬화 구조,
 * 방어적 복사 동작을 검증합니다.
 */
@DisplayName("PageResponse 페이지네이션 응답 래퍼 단위 테스트")
class PageResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ── totalPages 계산 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("totalPages 계산 — of() 팩토리 메서드")
    class TotalPagesCalculation {

        @Test
        @DisplayName("나누어 떨어지지 않는 경우 천장값(ceil)으로 올림 계산된다")
        void of_withNonExactDivision_roundsUpTotalPages() {
            final PageResponse<String> response = PageResponse.of(
                List.of("a", "b", "c"), 10L, 0, 3
            );

            assertThat(response.totalPages()).isEqualTo(4);
        }

        @Test
        @DisplayName("나누어 떨어지는 경우 정확한 페이지 수가 계산된다")
        void of_withExactDivision_calculatesExactTotalPages() {
            final PageResponse<String> response = PageResponse.of(
                List.of("a", "b", "c"), 9L, 0, 3
            );

            assertThat(response.totalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("totalCount가 0인 경우 totalPages가 0이다")
        void of_withZeroTotalCount_returnsTotalPagesZero() {
            final PageResponse<String> response = PageResponse.of(
                List.of(), 0L, 0, 10
            );

            assertThat(response.totalPages()).isEqualTo(0);
        }

        @Test
        @DisplayName("size가 0인 경우 totalPages가 0이다 (divide-by-zero 방지)")
        void of_withZeroSize_returnsTotalPagesZero() {
            final PageResponse<String> response = PageResponse.of(
                List.of(), 100L, 0, 0
            );

            assertThat(response.totalPages()).isEqualTo(0);
        }

        @Test
        @DisplayName("totalCount가 size보다 작은 경우 totalPages가 1이다")
        void of_withTotalCountLessThanSize_returnsTotalPagesOne() {
            final PageResponse<String> response = PageResponse.of(
                List.of("a"), 3L, 0, 10
            );

            assertThat(response.totalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("totalCount가 size와 동일한 경우 totalPages가 1이다")
        void of_withTotalCountEqualToSize_returnsTotalPagesOne() {
            final PageResponse<String> response = PageResponse.of(
                List.of("a", "b", "c"), 3L, 0, 3
            );

            assertThat(response.totalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("totalCount=1, size=1인 경우 totalPages가 1이다")
        void of_withSingleItemSingleSize_returnsTotalPagesOne() {
            final PageResponse<String> response = PageResponse.of(
                List.of("a"), 1L, 0, 1
            );

            assertThat(response.totalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("totalCount=100, size=10인 경우 totalPages가 10이다")
        void of_withHundredItemsPageSizeTen_returnsTenPages() {
            final PageResponse<String> response = PageResponse.of(
                List.of(), 100L, 0, 10
            );

            assertThat(response.totalPages()).isEqualTo(10);
        }
    }

    // ── of() 팩토리 — 기본 필드 설정 ─────────────────────────────────────────

    @Nested
    @DisplayName("of() 팩토리 메서드 — 기본 필드 설정")
    class OfFactory {

        @Test
        @DisplayName("content 필드가 전달된 목록과 동일하다")
        void of_setsContentCorrectly() {
            final List<String> items = List.of("item1", "item2");
            final PageResponse<String> response = PageResponse.of(items, 2L, 0, 10);

            assertThat(response.content()).containsExactly("item1", "item2");
        }

        @Test
        @DisplayName("totalCount 필드가 전달된 값과 동일하다")
        void of_setsTotalCountCorrectly() {
            final PageResponse<String> response = PageResponse.of(List.of(), 42L, 0, 10);

            assertThat(response.totalCount()).isEqualTo(42L);
        }

        @Test
        @DisplayName("page 필드가 전달된 값과 동일하다")
        void of_setsPageCorrectly() {
            final PageResponse<String> response = PageResponse.of(List.of(), 0L, 3, 10);

            assertThat(response.page()).isEqualTo(3);
        }

        @Test
        @DisplayName("size 필드가 전달된 값과 동일하다")
        void of_setsSizeCorrectly() {
            final PageResponse<String> response = PageResponse.of(List.of(), 0L, 0, 20);

            assertThat(response.size()).isEqualTo(20);
        }

        @Test
        @DisplayName("content가 null인 경우 빈 리스트로 대체된다")
        void of_withNullContent_usesEmptyList() {
            final PageResponse<String> response = PageResponse.of(null, 0L, 0, 10);

            assertThat(response.content()).isEmpty();
        }
    }

    // ── 방어적 복사 (Defensive Copy) ────────────────────────────────────────────

    @Nested
    @DisplayName("방어적 복사 — content 리스트 불변성")
    class DefensiveCopy {

        @Test
        @DisplayName("생성자에 전달한 가변 리스트를 수정해도 응답의 content에 영향이 없다")
        void content_defensivelyCopied_originalMutationHasNoEffect() {
            final List<String> mutableList = new ArrayList<>();
            mutableList.add("item1");

            final PageResponse<String> response = PageResponse.of(mutableList, 1L, 0, 10);
            mutableList.add("item2");

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0)).isEqualTo("item1");
        }

        @Test
        @DisplayName("응답의 content 리스트는 수정 불가능하다 (UnsupportedOperationException)")
        void content_returnedList_isImmutable() {
            final PageResponse<String> response = PageResponse.of(
                List.of("item1"), 1L, 0, 10
            );

            assertThatThrownBy(() -> response.content().add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("compact constructor에 직접 전달한 가변 리스트를 수정해도 영향이 없다")
        void content_compactConstructor_defensivelyCopied() {
            final List<String> mutableList = new ArrayList<>(List.of("a", "b"));
            final PageResponse<String> response = new PageResponse<>(mutableList, 2L, 1, 0, 10);
            mutableList.add("c");

            assertThat(response.content()).hasSize(2);
        }
    }

    // ── JSON 직렬화 구조 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("JSON 직렬화 — 5개 필드 구조 일관성")
    class JsonSerializationStructure {

        @Test
        @DisplayName("JSON 직렬화 시 content·totalCount·totalPages·page·size 5개 필드 포함")
        void serializedJson_containsAllFiveFields() throws Exception {
            final PageResponse<String> response = PageResponse.of(
                List.of("a"), 5L, 0, 10
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.has("content")).isTrue();
            assertThat(json.has("totalCount")).isTrue();
            assertThat(json.has("totalPages")).isTrue();
            assertThat(json.has("page")).isTrue();
            assertThat(json.has("size")).isTrue();
        }

        @Test
        @DisplayName("JSON 직렬화 시 정확히 5개의 최상위 필드를 가진다")
        void serializedJson_hasExactlyFiveTopLevelFields() throws Exception {
            final PageResponse<String> response = PageResponse.of(
                List.of("item"), 1L, 0, 10
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.size()).isEqualTo(5);
        }

        @Test
        @DisplayName("JSON 직렬화 시 content가 배열로 직렬화된다")
        void serializedJson_contentIsArray() throws Exception {
            final PageResponse<String> response = PageResponse.of(
                List.of("a", "b"), 2L, 0, 10
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.get("content").isArray()).isTrue();
            assertThat(json.get("content").size()).isEqualTo(2);
        }

        @Test
        @DisplayName("JSON 직렬화 시 totalCount 값이 올바르다")
        void serializedJson_totalCountValueIsCorrect() throws Exception {
            final PageResponse<String> response = PageResponse.of(
                List.of("a"), 99L, 0, 10
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.get("totalCount").asLong()).isEqualTo(99L);
        }

        @Test
        @DisplayName("JSON 직렬화 시 totalPages 값이 계산된 값과 동일하다")
        void serializedJson_totalPagesMatchesCalculation() throws Exception {
            final PageResponse<String> response = PageResponse.of(
                List.of(), 25L, 0, 10
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.get("totalPages").asInt()).isEqualTo(3);
        }

        @Test
        @DisplayName("JSON 직렬화 시 page와 size 값이 올바르다")
        void serializedJson_pageAndSizeValuesAreCorrect() throws Exception {
            final PageResponse<String> response = PageResponse.of(
                List.of(), 50L, 2, 15
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.get("page").asInt()).isEqualTo(2);
            assertThat(json.get("size").asInt()).isEqualTo(15);
        }

        @Test
        @DisplayName("빈 content 목록도 JSON 배열로 직렬화된다")
        void serializedJson_emptyContentIsEmptyArray() throws Exception {
            final PageResponse<String> response = PageResponse.of(
                List.of(), 0L, 0, 10
            );
            final JsonNode json = objectMapper.valueToTree(response);

            assertThat(json.get("content").isArray()).isTrue();
            assertThat(json.get("content").size()).isEqualTo(0);
        }

        @Test
        @DisplayName("레코드 타입 content도 JSON 중첩 객체 배열로 직렬화된다")
        void serializedJson_recordContentSerializedAsObjectArray() throws Exception {
            record Item(String name, int value) { }

            final PageResponse<Item> response = PageResponse.of(
                List.of(new Item("test", 42)), 1L, 0, 10
            );
            final JsonNode json = objectMapper.valueToTree(response);
            final JsonNode firstItem = json.get("content").get(0);

            assertThat(firstItem.get("name").asText()).isEqualTo("test");
            assertThat(firstItem.get("value").asInt()).isEqualTo(42);
        }
    }

    // ── ApiResponse와 PageResponse 통합 래핑 ──────────────────────────────────

    @Nested
    @DisplayName("ApiResponse Envelope 안에 PageResponse 래핑 — 중첩 직렬화")
    class WrappedInApiResponse {

        @Test
        @DisplayName("ApiResponse<PageResponse<T>>가 올바르게 직렬화된다")
        void apiResponse_wrappingPageResponse_serializedCorrectly() throws Exception {
            final PageResponse<String> page = PageResponse.of(
                List.of("item1", "item2"), 20L, 1, 10
            );
            final ApiResponse<PageResponse<String>> response = ApiResponse.success("조회 성공", page);
            final JsonNode json = objectMapper.valueToTree(response);
            final JsonNode dataNode = json.get("data");

            assertThat(json.get("code").asText()).isEqualTo("SUCCESS");
            assertThat(dataNode).isNotNull();
            assertThat(dataNode.has("content")).isTrue();
            assertThat(dataNode.has("totalCount")).isTrue();
            assertThat(dataNode.has("totalPages")).isTrue();
            assertThat(dataNode.has("page")).isTrue();
            assertThat(dataNode.has("size")).isTrue();
            assertThat(dataNode.get("totalCount").asLong()).isEqualTo(20L);
            assertThat(dataNode.get("page").asInt()).isEqualTo(1);
        }
    }
}
