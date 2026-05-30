plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.anchors"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation(libs.mybatis.spring.boot.starter)

    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 일부 Docker 엔진(예: 특정 Docker Desktop 버전)에서는 docker-java 가 협상하는 API 버전을
    // 데몬이 거부해 Testcontainers 가 "valid Docker environment 없음"으로 실패할 수 있다.
    // 그런 환경에서만 로컬 ~/.gradle/gradle.properties 에 `dockerApiVersion=1.43` 을 설정해 옵트인한다.
    // 공유 빌드 스크립트에는 특정 버전을 하드코딩하지 않는다.
    (project.findProperty("dockerApiVersion") as String?)?.let {
        systemProperty("api.version", it)
    }
}
