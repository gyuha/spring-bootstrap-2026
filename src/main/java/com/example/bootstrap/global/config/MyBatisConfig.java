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
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:mapper/**/*.xml"));
        return factory.getObject();
    }
}
