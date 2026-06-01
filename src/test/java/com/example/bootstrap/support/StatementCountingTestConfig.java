package com.example.bootstrap.support;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link StatementCountingInterceptor}를 테스트 {@code SqlSessionFactory}에 등록하는 테스트 설정
 * (N+1 부재 프로그램적 박제, 04-05/QUERY-01).
 *
 * <p>운영 {@code MyBatisConfig}는 {@code SqlSessionFactoryBean.getObject()}로 팩토리를 직접 만들어
 * 빈으로 노출한다. {@code ConfigurationCustomizer}(스타터 자동설정 경로)는 이 수동 팩토리에 적용되지
 * 않으므로, {@link BeanPostProcessor}로 이미 생성된 {@link SqlSessionFactory} 빈의 가변
 * {@code Configuration}에 인터셉터를 추가한다(0 new deps, D-82).
 */
@TestConfiguration
public class StatementCountingTestConfig {

    @Bean
    public StatementCountingInterceptor statementCountingInterceptor() {
        return new StatementCountingInterceptor();
    }

    @Bean
    public static BeanPostProcessor statementCountingRegistrar(
            org.springframework.beans.factory.ObjectProvider<StatementCountingInterceptor> provider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof SqlSessionFactory factory) {
                    factory.getConfiguration().addInterceptor(provider.getObject());
                }
                return bean;
            }
        };
    }
}
