package com.anchors.baseline.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * PLAT-02 / D-05: 헥사고날 + DDD 계층 의존 방향을 ArchUnit 으로 강제한다.
 *
 * <pre>
 *   interfaces → application → domain
 *   infrastructure → domain, application (포트 구현)
 *   domain 은 어떤 계층도 의존하지 않는다
 * </pre>
 *
 * <p>{@code ..domain..} 두 점 패턴으로 컨텍스트 하위 중첩 패키지까지 매칭한다.
 */
class ArchitectureTest {

    private static final String ROOT = "com.anchors.baseline";

    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages(ROOT);

    @Test
    void hexagonalLayerDependencies() {
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(ROOT + "..domain..")
            .layer("Application").definedBy(ROOT + "..application..")
            .layer("Infrastructure").definedBy(ROOT + "..infrastructure..")
            .layer("Interfaces").definedBy(ROOT + "..interfaces..")
            .whereLayer("Domain").mayNotAccessAnyLayer()
            .whereLayer("Application").mayOnlyAccessLayers("Domain")
            .whereLayer("Interfaces").mayOnlyAccessLayers("Application", "Domain")
            .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Application")
            .check(classes);
    }
}
