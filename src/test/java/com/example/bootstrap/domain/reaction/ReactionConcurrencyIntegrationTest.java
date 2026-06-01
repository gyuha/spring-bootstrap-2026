package com.example.bootstrap.domain.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.bootstrap.domain.board.entity.Board;
import com.example.bootstrap.domain.board.repository.BoardRepository;
import com.example.bootstrap.domain.post.entity.Post;
import com.example.bootstrap.domain.post.repository.PostRepository;
import com.example.bootstrap.domain.reaction.entity.ReactionTarget;
import com.example.bootstrap.domain.reaction.entity.ReactionType;
import com.example.bootstrap.domain.reaction.mapper.ReactionMapper;
import com.example.bootstrap.domain.reaction.service.ReactionService;
import com.example.bootstrap.global.BaseIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MANDATORY 동시 2요청 리액션 멱등성 통합 테스트 (SC3, P6, T-04-15) — mock 없는 실 PG17.
 *
 * <p>같은 {@code (POST, postId, userId)}로 {@link ReactionService#set}을 2개 스레드에서
 * {@link CountDownLatch} 시작 래치로 동시에 호출해 upsert 경합을 만든다. {@link ReactionService#set}은
 * {@code @Transactional}이고, 각 워커 스레드는 기존 트랜잭션 컨텍스트가 없으므로 각자 독립 물리
 * 트랜잭션을 연다(경합이 실제로 발생). 다음을 모두 단언한다:
 *
 * <ol>
 *   <li>두 Future 모두 예외 없이 완료 — plain INSERT 구현이면 둘 중 하나가 UNIQUE 제약 위반으로
 *       {@code DataIntegrityViolationException}을 던져 이 단언이 실패한다. 오직 {@code ON CONFLICT
 *       DO UPDATE} upsert만 두 요청을 모두 성공시킨다("운 좋은 실패" 배제, I-1).
 *   <li>reactions 행 COUNT == 1 (중복 행 0).
 *   <li>likeCount == 1 (카운트 +2 없음).
 * </ol>
 *
 * <p>가상 스레드 핀닝 주의(P1): 동기화는 {@link CountDownLatch}만 사용하고 DB 호출을
 * {@code synchronized}로 감싸지 않는다.
 */
class ReactionConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    ReactionService reactionService;

    @Autowired
    ReactionMapper reactionMapper;

    @Autowired
    PostRepository postRepository;

    @Autowired
    BoardRepository boardRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    /** 같은 type(LIKE) 동시 2요청 → 두 Future 성공 + 행 1 + likeCount 1. */
    @Test
    void concurrentSameTypeReaction_bothSucceed_singleRow_countOne() throws Exception {
        UUID postId = seedPost();
        UUID userId = UUID.randomUUID();

        List<Future<?>> futures = runConcurrently(
                () -> reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.LIKE),
                () -> reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.LIKE));

        assertBothFuturesSucceed(futures);
        assertThat(rowCount(postId, userId)).as("중복 행 0 — 정확히 1행").isEqualTo(1L);
        assertThat(reactionMapper.countByTarget(ReactionTarget.POST.name(), postId, "LIKE"))
                .as("카운트 +2 없음 — likeCount 1")
                .isEqualTo(1L);
    }

    /** like와 dislike 동시 2요청 → 두 Future 성공 + 최종 1행(type은 둘 중 하나). */
    @Test
    void concurrentLikeAndDislike_bothSucceed_singleRow() throws Exception {
        UUID postId = seedPost();
        UUID userId = UUID.randomUUID();

        List<Future<?>> futures = runConcurrently(
                () -> reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.LIKE),
                () -> reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.DISLIKE));

        assertBothFuturesSucceed(futures);
        assertThat(rowCount(postId, userId)).as("최종 1행").isEqualTo(1L);
        long like = reactionMapper.countByTarget(ReactionTarget.POST.name(), postId, "LIKE");
        long dislike = reactionMapper.countByTarget(ReactionTarget.POST.name(), postId, "DISLIKE");
        assertThat(like + dislike).as("type은 둘 중 하나 — 합계 1").isEqualTo(1L);
    }

    // --- helpers ---

    /** 시작 래치로 2 작업을 동시에 발사하고 Future를 수집한다(ExecutorService(2) + CountDownLatch). */
    private List<Future<?>> runConcurrently(Runnable a, Runnable b) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> f1 = submitGated(pool, start, a);
            Future<?> f2 = submitGated(pool, start, b);
            start.countDown(); // 동시 발사
            return List.of(f1, f2);
        } finally {
            pool.shutdown();
        }
    }

    private Future<?> submitGated(ExecutorService pool, CountDownLatch start, Runnable task) {
        return pool.submit(() -> {
            await(start);
            task.run();
            return null;
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** 두 Future 모두 예외 없이 완료됨을 단언(ExecutionException/DataIntegrityViolation 없음, I-1). */
    private void assertBothFuturesSucceed(List<Future<?>> futures) {
        for (int i = 0; i < futures.size(); i++) {
            Future<?> f = futures.get(i);
            int idx = i;
            assertThatCode(f::get)
                    .as("future[%d]는 예외 없이 성공해야 한다(plain INSERT의 운 좋은 실패 배제)", idx)
                    .doesNotThrowAnyException();
        }
    }

    private long rowCount(UUID postId, UUID userId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reactions "
                        + "WHERE target_type = 'POST' AND target_id = ? AND user_id = ?",
                Long.class,
                postId,
                userId);
        return n == null ? 0L : n;
    }

    /** 경합 대상 post 1건을 별도 트랜잭션으로 미리 커밋한다(워커 스레드가 볼 수 있도록). */
    private UUID seedPost() {
        return transactionTemplate.execute(status -> {
            Board board = boardRepository.save(Board.create("board-" + UUID.randomUUID()));
            Post post = postRepository.save(
                    Post.create(board.getId(), UUID.randomUUID(), "title", "content"));
            return post.getId();
        });
    }
}
