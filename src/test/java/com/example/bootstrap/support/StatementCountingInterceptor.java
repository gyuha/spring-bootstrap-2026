package com.example.bootstrap.support;

import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

/**
 * 실행된 SQL 문(statement) 수를 카운트하는 MyBatis 인터셉터 (N+1 부재 프로그램적 박제, 04-05/QUERY-01).
 *
 * <p><b>0 new deps:</b> {@code mybatis-spring-boot-starter}가 이미 클래스패스에 있으므로
 * {@code org.apache.ibatis.plugin.Interceptor}를 신규 의존성 없이 구현한다(D-82).
 *
 * <p><b>왜 StatementHandler.prepare인가:</b> {@code StatementHandler.prepare(Connection, Integer)}는
 * MyBatis가 JDBC {@code PreparedStatement}를 실제로 준비하는 지점이다 — SELECT/INSERT/UPDATE/DELETE
 * 매 SQL 문마다 정확히 1회 호출된다. MyBatis 1차 캐시(local cache) 히트 시에는 prepare가 호출되지
 * 않으므로, 본 카운터는 "실제로 DB에 나간 SQL 문 수"를 잰다. 행별 상관 서브쿼리나 N+1 반복 쿼리가
 * 있으면 목록 크기에 비례해 증가하고, 단일 GROUP BY 집계면 목록 크기와 무관하게 상수다.
 *
 * <p>테스트는 {@link #reset()}으로 0으로 초기화한 뒤 조회를 수행하고 {@link #getCount()}로 실행
 * 문 수를 읽는다. {@code @TestConfiguration}에서 테스트 {@code SqlSessionFactory}에 등록한다.
 */
@Intercepts({
    @Signature(
            type = StatementHandler.class,
            method = "prepare",
            args = {Connection.class, Integer.class})
})
public class StatementCountingInterceptor implements Interceptor {

    private final AtomicInteger count = new AtomicInteger(0);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        count.incrementAndGet();
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 설정 프로퍼티 없음.
    }

    /** 카운터를 0으로 초기화한다(측정 시작 직전 호출). */
    public void reset() {
        count.set(0);
    }

    /** reset 이후 실행된 SQL 문(prepare) 수. */
    public int getCount() {
        return count.get();
    }
}
