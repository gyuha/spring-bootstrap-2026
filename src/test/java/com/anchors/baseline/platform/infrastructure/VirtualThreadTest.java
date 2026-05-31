package com.anchors.baseline.platform.infrastructure;

import com.anchors.baseline.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLAT-01: 가상 스레드가 활성화되어 동작하는지 검증한다.
 */
class VirtualThreadTest extends AbstractIntegrationTest {

    @Value("${spring.threads.virtual.enabled:false}")
    boolean virtualThreadsEnabled;

    @Test
    void virtualThreadEnabled() throws InterruptedException {
        AtomicBoolean virtual = new AtomicBoolean(false);
        Thread t = Thread.ofVirtual().start(() -> virtual.set(Thread.currentThread().isVirtual()));
        t.join();
        assertThat(virtual.get()).isTrue();
    }

    @Test
    void springVirtualThreadsEnabled() {
        assertThat(virtualThreadsEnabled).isTrue();
    }
}
