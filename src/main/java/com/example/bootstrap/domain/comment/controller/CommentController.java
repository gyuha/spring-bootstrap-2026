package com.example.bootstrap.domain.comment.controller;

import com.example.bootstrap.domain.comment.dto.CommentCreateRequest;
import com.example.bootstrap.domain.comment.dto.CommentListItem;
import com.example.bootstrap.domain.comment.dto.CommentResponse;
import com.example.bootstrap.domain.comment.service.CommentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 댓글 작성/조회/삭제 API (CMNT-01~04).
 *
 * <p>작성·조회·삭제는 인증 USER면 충분(SecurityConfig {@code anyRequest().authenticated()}).
 * 작성자 식별과 삭제 소유권은 {@link CurrentUser}(JWT subject=userId)에서 userId/isAdmin을 추출해
 * 서비스에 전달한다(D-75). 작성자는 요청 본문이 아닌 JWT subject에서만 설정한다(T-04-11). 목록
 * page/size는 {@code @Validated} + 경계로 거대 size DoS를 차단한다(PostController/AdminUserController 패턴).
 */
@RestController
@RequiredArgsConstructor
@Validated
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentResponse> create(
            @PathVariable UUID postId, @Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.ok(commentService.create(
                postId, CurrentUser.userId(), request.parentCommentId(), request.content()));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<PageResponse<CommentListItem>> list(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(commentService.findPage(postId, page, size));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID commentId) {
        commentService.delete(commentId, CurrentUser.userId(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }
}
