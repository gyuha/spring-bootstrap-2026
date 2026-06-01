package com.example.bootstrap.domain.board;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bootstrap.domain.user.entity.Role;
import com.example.bootstrap.global.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 4 board→post→comment→reaction 전 흐름 + 인가 회귀 통합 테스트 (SC1/SC2, BOARD-01~05, CMNT-01~04)
 * — mock 없는 실 PG17+Redis7 검증.
 *
 * <p>소유권 제어를 단순화하기 위해 작성자 토큰은 {@code jwtTokenProvider.issueAccess(고정 userId,
 * USER)}로 직접 발급한다(author_id == JWT subject). 같은 userId 토큰=본인, 다른 userId 토큰=타인,
 * ADMIN 토큰=관리자. author_id 컬럼에는 FK가 없으므로(V3) 임의 UUID로 충분하다. 데이터 격리는 본
 * 테스트가 생성한 board/post/comment만 단언한다(AdminUserFlowIntegrationTest 교훈).
 */
@AutoConfigureMockMvc
class BoardFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    /** 1) board: ADMIN 생성 201, USER 생성 403(ACCESS_DENIED — @PreAuthorize). */
    @Test
    void board_adminCreates201_userForbidden403() throws Exception {
        mockMvc.perform(post("/boards")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"board-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty());

        mockMvc.perform(post("/boards")
                        .header("Authorization", userBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"board-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    /**
     * 2) post 소유권/soft-delete: USER 작성 201 → 단건/목록 200 → 본인 PUT 200 → 타인 PUT/DELETE 403 →
     * ADMIN이 타인 글 DELETE 200(soft-delete) → 삭제 후 단건 404·목록 제외.
     */
    @Test
    void post_crud_ownership_softDeleteExclusion() throws Exception {
        UUID boardId = createBoard();
        UUID authorId = UUID.randomUUID();
        String author = userToken(authorId);
        String other = userToken(UUID.randomUUID());

        UUID postId = createPost(boardId, author);

        // 단건/목록 조회 200
        mockMvc.perform(get("/posts/" + postId).header("Authorization", other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId.toString()))
                .andExpect(jsonPath("$.data.authorId").value(authorId.toString()));
        mockMvc.perform(get("/boards/" + boardId + "/posts?page=0&size=100")
                        .header("Authorization", other))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.content[?(@.id=='" + postId + "')].likeCount").value(
                        org.hamcrest.Matchers.contains(0)));

        // 작성자 본인 PUT 200
        mockMvc.perform(put("/posts/" + postId)
                        .header("Authorization", author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"updated\",\"content\":\"updated body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("updated"));

        // 타인 PUT/DELETE 403(FORBIDDEN_OPERATION)
        mockMvc.perform(put("/posts/" + postId)
                        .header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
        mockMvc.perform(delete("/posts/" + postId).header("Authorization", other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));

        // ADMIN이 타인 글 삭제 200(204 noContent) — soft-delete
        mockMvc.perform(delete("/posts/" + postId).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        // 삭제 후 단건 404, 목록 제외
        mockMvc.perform(get("/posts/" + postId).header("Authorization", author))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_NOT_FOUND"));
        String list = mockMvc.perform(get("/boards/" + boardId + "/posts?page=0&size=100")
                        .header("Authorization", author))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
                        (java.util.List<?>) JsonPath.read(
                                list, "$.data.content[?(@.id=='" + postId + "')]"))
                .isEmpty();
    }

    /**
     * 3) comment: 루트 댓글 + 1단계 대댓글 작성 201, 대댓글의 대댓글 거부(403), 목록 soft-delete 제외,
     * 작성자/ADMIN 삭제 204·타인 403.
     */
    @Test
    void comment_reply_oneLevel_ownership_softDelete() throws Exception {
        UUID boardId = createBoard();
        UUID authorId = UUID.randomUUID();
        String author = userToken(authorId);
        String other = userToken(UUID.randomUUID());
        UUID postId = createPost(boardId, author);

        // 루트 댓글
        UUID rootId = createComment(postId, author, null);
        // 1단계 대댓글
        UUID replyId = createComment(postId, author, rootId);

        // 대댓글의 대댓글 → 거부(FORBIDDEN_OPERATION)
        mockMvc.perform(post("/posts/" + postId + "/comments")
                        .header("Authorization", author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"reply-to-reply\",\"parentCommentId\":\""
                                + replyId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));

        // 타인 댓글 삭제 403
        mockMvc.perform(delete("/comments/" + rootId).header("Authorization", other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));

        // 작성자 본인 댓글(reply) 삭제 204, ADMIN이 root 삭제 204
        mockMvc.perform(delete("/comments/" + replyId).header("Authorization", author))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/comments/" + rootId).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        // 목록에서 삭제 댓글 제외
        String list = mockMvc.perform(get("/posts/" + postId + "/comments?page=0&size=100")
                        .header("Authorization", author))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
                        (java.util.List<?>) JsonPath.read(
                                list, "$.data.content[?(@.id=='" + rootId + "')]"))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(
                        (java.util.List<?>) JsonPath.read(
                                list, "$.data.content[?(@.id=='" + replyId + "')]"))
                .isEmpty();
    }

    /** 4) 리액션: post/comment set 200(noContent) → remove 204 → 삭제 target 리액션 404. */
    @Test
    void reaction_setRemove_deletedTarget404() throws Exception {
        UUID boardId = createBoard();
        String author = userToken(UUID.randomUUID());
        UUID postId = createPost(boardId, author);
        UUID commentId = createComment(postId, author, null);

        // post 리액션 set (PUT, ApiResponse.noContent → 200)
        mockMvc.perform(put("/posts/" + postId + "/reactions")
                        .header("Authorization", author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LIKE\"}"))
                .andExpect(status().isOk());
        // 목록 likeCount == 1
        mockMvc.perform(get("/boards/" + boardId + "/posts?page=0&size=100")
                        .header("Authorization", author))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.content[?(@.id=='" + postId + "')].likeCount").value(
                        org.hamcrest.Matchers.contains(1)));

        // comment 리액션 set
        mockMvc.perform(put("/comments/" + commentId + "/reactions")
                        .header("Authorization", author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DISLIKE\"}"))
                .andExpect(status().isOk());

        // remove 204
        mockMvc.perform(delete("/posts/" + postId + "/reactions").header("Authorization", author))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/comments/" + commentId + "/reactions")
                        .header("Authorization", author))
                .andExpect(status().isNoContent());

        // 삭제 target(존재하지 않는 post)에 리액션 → 404(REACTION_TARGET_NOT_FOUND)
        mockMvc.perform(delete("/posts/" + postId).header("Authorization", admin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/posts/" + postId + "/reactions")
                        .header("Authorization", author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LIKE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REACTION_TARGET_NOT_FOUND"));
    }

    /** 5) 미인증 mutation → 401(UNAUTHENTICATED). */
    @Test
    void unauthenticated_mutation_returns401() throws Exception {
        mockMvc.perform(post("/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/boards/" + UUID.randomUUID() + "/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    // --- helpers ---

    private String admin() {
        return "Bearer " + jwtTokenProvider.issueAccess(UUID.randomUUID(), Role.ADMIN).token();
    }

    private String userToken(UUID userId) {
        return "Bearer " + jwtTokenProvider.issueAccess(userId, Role.USER).token();
    }

    private UUID createBoard() throws Exception {
        String json = mockMvc.perform(post("/boards")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"board-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.data.id"));
    }

    private UUID createPost(UUID boardId, String authorToken) throws Exception {
        String json = mockMvc.perform(post("/boards/" + boardId + "/posts")
                        .header("Authorization", authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"title\",\"content\":\"content body\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.data.id"));
    }

    private UUID createComment(UUID postId, String authorToken, UUID parentId) throws Exception {
        String parentJson = parentId == null ? "null" : "\"" + parentId + "\"";
        String json = mockMvc.perform(post("/posts/" + postId + "/comments")
                        .header("Authorization", authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"comment body\",\"parentCommentId\":" + parentJson
                                + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.data.id"));
    }
}
