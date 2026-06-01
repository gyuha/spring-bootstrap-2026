package com.example.bootstrap.domain.reaction.service;

import com.example.bootstrap.domain.comment.repository.CommentRepository;
import com.example.bootstrap.domain.post.repository.PostRepository;
import com.example.bootstrap.domain.reaction.entity.ReactionTarget;
import com.example.bootstrap.domain.reaction.entity.ReactionType;
import com.example.bootstrap.domain.reaction.mapper.ReactionMapper;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리액션 set/remove 비즈니스 로직 + 트랜잭션 경계 (BOARD-06/CMNT-05, D-73/D-74/D-75b).
 *
 * <p>reaction은 JPA {@code @Entity}/Repository가 없는 MyBatis 전용 모델이다(C-1). set은 (user,target)당
 * 1행 멱등 upsert({@link ReactionMapper#upsert}: {@code INSERT ... ON CONFLICT DO UPDATE}), remove는
 * 하드 DELETE다 — 동시 2요청에도 중복 행/카운트 +2가 없음을 DB {@code UNIQUE(target_type,target_id,
 * user_id)} 제약이 보장한다(P6, 동시성 멱등 통합 검증은 04-05의 MANDATORY 테스트). set 전에 target
 * 존재를 Post/CommentRepository.findById({@code @SQLRestriction}로 soft-delete 자동 제외)로 확인해
 * 삭제·부재 target 리액션을 {@link ErrorCode#REACTION_TARGET_NOT_FOUND}(404)로 차단한다(D-75b/P7).
 * enum은 {@code .name()} 문자열로 매퍼에 바인딩한다(VARCHAR 컬럼, D-78). user_id는 본문이 아닌 JWT
 * subject(CurrentUser.userId)에서만 전달받는다(T-04-14 mass-assignment 방지).
 */
@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionMapper reactionMapper;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public void set(ReactionTarget target, UUID targetId, UUID userId, ReactionType type) {
        requireTargetExists(target, targetId);
        reactionMapper.upsert(target.name(), targetId, userId, type.name());
    }

    @Transactional
    public void remove(ReactionTarget target, UUID targetId, UUID userId) {
        reactionMapper.delete(target.name(), targetId, userId);
    }

    /**
     * target(POST/COMMENT)이 존재하고 soft-delete되지 않았는지 확인한다(D-75b). Post/CommentRepository의
     * {@code @SQLRestriction("deleted_at is null")}이 삭제 행을 findById에서 자동 제외하므로, 없으면
     * {@link ErrorCode#REACTION_TARGET_NOT_FOUND}(404)로 차단한다(P7).
     */
    private void requireTargetExists(ReactionTarget target, UUID targetId) {
        boolean exists = switch (target) {
            case POST -> postRepository.findById(targetId).isPresent();
            case COMMENT -> commentRepository.findById(targetId).isPresent();
        };
        if (!exists) {
            throw new BusinessException(ErrorCode.REACTION_TARGET_NOT_FOUND);
        }
    }
}
