package com.example.bootstrap.global.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.i18n.LocaleContextResolver;

/**
 * i18n 국제화 설정 클래스.
 *
 * <p>Spring WebFlux 환경에서 Accept-Language 헤더 기반의 다국어 지원을 구성합니다.
 * 지원 언어: 한국어(ko), 영어(en) — 기본값: 한국어
 *
 * <p>{@code localeContextResolver} 빈은 Spring WebFlux의 {@code WebFluxConfigurationSupport}
 * 가 생성하는 기본 {@code FixedLocaleContextResolver} 를 대체합니다.
 * {@code spring.main.allow-bean-definition-overriding=true} 설정이 필요합니다.
 */
@Configuration
public class I18nConfig {

    /** 메시지 리소스 베이스 경로 (언어 접미사 자동 탐색). */
    private static final String MESSAGES_BASENAME = "classpath:i18n/messages";

    /** 메시지 소스 캐시 갱신 주기 (초). */
    private static final int CACHE_SECONDS = 3600;

    /**
     * Accept-Language 헤더 기반 {@link LocaleContextResolver} 빈을 등록합니다.
     *
     * <p>한국어(ko)와 영어(en)를 지원하며, 헤더가 없거나 지원하지 않는 언어인 경우
     * 기본값으로 한국어를 사용합니다.
     * 빈 이름 {@code localeContextResolver} 는 Spring WebFlux 프레임워크가
     * {@code HttpWebHandlerAdapter} 초기화 시 자동으로 탐색하는 이름입니다.
     *
     * @return 설정된 {@link AcceptHeaderLocaleContextResolver} 인스턴스
     */
    @Bean
    public LocaleContextResolver localeContextResolver() {
        final AcceptHeaderLocaleContextResolver resolver = new AcceptHeaderLocaleContextResolver();
        resolver.setSupportedLocales(List.of(Locale.KOREAN, Locale.ENGLISH));
        resolver.setDefaultLocale(Locale.KOREAN);
        return resolver;
    }

    /**
     * 재로드 가능한 번들 MessageSource 빈을 등록합니다.
     *
     * <p>{@code classpath:i18n/messages_ko.properties} 및
     * {@code classpath:i18n/messages_en.properties} 파일에서 메시지를 로드합니다.
     * UTF-8 인코딩, 기본 로케일 한국어, 1시간 캐시를 사용합니다.
     * 메시지 코드가 없는 경우 코드 자체를 메시지로 반환합니다.
     *
     * @return 설정된 {@link MessageSource} 인스턴스
     */
    @Bean
    public MessageSource messageSource() {
        final ReloadableResourceBundleMessageSource messageSource =
            new ReloadableResourceBundleMessageSource();
        messageSource.setBasename(MESSAGES_BASENAME);
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setDefaultLocale(Locale.KOREAN);
        messageSource.setCacheSeconds(CACHE_SECONDS);
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }
}
