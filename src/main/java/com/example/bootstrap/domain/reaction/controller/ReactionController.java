package com.example.bootstrap.domain.reaction.controller;

import com.example.bootstrap.domain.reaction.dto.ReactionSetRequest;
import com.example.bootstrap.domain.reaction.entity.ReactionTarget;
import com.example.bootstrap.domain.reaction.service.ReactionService;
import com.example.bootstrap.global.response.ApiResponse;
import com.example.bootstrap.global.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글/댓글 리액션 set/remove API (BOARD-06/CMNT-05, D-74).
 *
 * <p>멱등 PUT(set)/DELETE(remove) 2엔드포인트만 채택하고 비멱등 토글 POST는 두지 않는다(D-74/P6) —
 * 같은 요청 2회가 +2/중복 행이 되지 않도록 멱등 verb를 쓴다. 인증 USER면 충분하다(SecurityConfig
 * {@code anyRequest().authenticated()}, D-75). user_id는 요청 본문이 아닌 JWT subject({@link
 * CurrentUser#userId()})에서만 설정한다(T-04-14 mass-assignment 방지).
 */
@RestController
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PutMapping("/posts/{postId}/reactions")
    public ApiResponse<Void> setPostReaction(
            @PathVariable UUID postId, @Valid @RequestBody ReactionSetRequest request) {
        reactionService.set(ReactionTarget.POST, postId, CurrentUser.userId(), request.type());
        return ApiResponse.noContent();
    }

    @DeleteMapping("/posts/{postId}/reactions")
    public ResponseEntity<Void> removePostReaction(@PathVariable UUID postId) {
        reactionService.remove(ReactionTarget.POST, postId, CurrentUser.userId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/comments/{commentId}/reactions")
    public ApiResponse<Void> setCommentReaction(
            @PathVariable UUID commentId, @Valid @RequestBody ReactionSetRequest request) {
        reactionService.set(ReactionTarget.COMMENT, commentId, CurrentUser.userId(), request.type());
        return ApiResponse.noContent();
    }

    @DeleteMapping("/comments/{commentId}/reactions")
    public ResponseEntity<Void> removeCommentReaction(@PathVariable UUID commentId) {
        reactionService.remove(ReactionTarget.COMMENT, commentId, CurrentUser.userId());
        return ResponseEntity.noContent().build();
    }
}
