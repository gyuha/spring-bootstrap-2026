package com.example.bootstrap.global.config;

import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * MyBatis 배선 — JPA와 동일 DataSource·단일 JpaTransactionManager 공유 (FOUND-02, D-08).
 *
 * <p>{@code domain} 패키지의 {@code @Mapper} 인터페이스를 자동 스캔하고, JPA가 사용하는 것과
 * 동일한 {@link DataSource}로 {@link SqlSessionFactory}를 만든다. 동일 DataSource이므로
 * MyBatis는 Spring 트랜잭션 동기화에 자동 참여해 JPA 쓰기와 같은 물리 트랜잭션을 공유한다.
 * 별도 트랜잭션 매니저 분리 금지(D-08).
 */
@Configuration
@MapperScan(basePackages = "com.example.bootstrap.domain", annotationClass = Mapper.class)
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        // classpath*: 는 mapper/ 디렉터리가 아직 없어도(Phase 1엔 XML 매퍼 없음) 빈 배열을 반환한다.
        // classpath:(단수)는 베이스 디렉터리 부재 시 FileNotFoundException을 던져 컨텍스트 로딩이 깨진다.
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
        return factory.getObject();
    }
}
