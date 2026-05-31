plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Flyway 오버라이드 — SB4.0.6 기본(11.14.1)에서 11.15.0으로 (PG18/PG17 호환, spring-boot#49012)
extra["flyway.version"] = "11.15.0"

repositories {
    mavenCentral()
}

dependencies {
    // Core Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Security (Phase 1은 배선만, JWT는 Phase 2)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // MyBatis (SB4 전용 4.0.x 라인)
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1")

    // Flyway (SB4: spring-boot-starter-flyway 별도 starter + PostgreSQL 모듈)
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // API 문서 (SB4 전용 v3.x — BOM 관리 아님, 명시 버전)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    // 로컬 인프라 자동기동
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // DB driver
    runtimeOnly("org.postgresql:postgresql")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
