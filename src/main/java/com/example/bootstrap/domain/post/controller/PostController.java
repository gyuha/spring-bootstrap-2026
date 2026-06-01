package com.example.bootstrap.domain.post.controller;

import com.example.bootstrap.domain.post.dto.PostCreateRequest;
import com.example.bootstrap.domain.post.dto.PostListItem;
import com.example.bootstrap.domain.post.dto.PostResponse;
import com.example.bootstrap.domain.post.dto.PostUpdateRequest;
import com.example.bootstrap.domain.post.service.PostService;
import com.example.bootstrap.global.response.ApiResponse;
import com.example.bootstrap.global.response.PageResponse;
import com.example.bootstrap.global.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 작성/조회/수정/삭제 API (BOARD-02~05).
 *
 * <p>작성·조회·수정·삭제는 인증 USER면 충분(SecurityConfig {@code anyRequest().authenticated()}).
 * 작성자 식별과 수정/삭제 소유권은 {@link CurrentUser}(JWT subject=userId)에서 userId/isAdmin을
 * 추출해 서비스에 전달한다(D-75). 작성자는 요청 본문이 아닌 JWT subject에서만 설정한다(T-04-07).
 * 목록 page/size는 {@code @Validated} + 경계로 거대 size DoS를 차단한다(T-04-05, AdminUserController 패턴).
 */
@RestController
@RequiredArgsConstructor
@Validated
public class PostController {

    private final PostService postService;

    @PostMapping("/boards/{boardId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostResponse> create(
            @PathVariable UUID boardId, @Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.ok(
                postService.create(boardId, CurrentUser.userId(), request.title(), request.content()));
    }

    @GetMapping("/boards/{boardId}/posts")
    public ApiResponse<PageResponse<PostListItem>> list(
            @PathVariable UUID boardId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(postService.findPage(boardId, page, size));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<PostResponse> get(@PathVariable UUID postId) {
        return ApiResponse.ok(postService.findOne(postId));
    }

    @PutMapping("/posts/{postId}")
    public ApiResponse<PostResponse> update(
            @PathVariable UUID postId, @Valid @RequestBody PostUpdateRequest request) {
        return ApiResponse.ok(postService.update(
                postId, CurrentUser.userId(), CurrentUser.isAdmin(),
                request.title(), request.content()));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> delete(@PathVariable UUID postId) {
        postService.delete(postId, CurrentUser.userId(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }
}
