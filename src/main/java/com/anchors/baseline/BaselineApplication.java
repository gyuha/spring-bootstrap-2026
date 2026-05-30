package com.anchors.baseline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class BaselineApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaselineApplication.class, args);
    }

    @Bean
    CommandLineRunner verifyVirtualThreads() {
        return args -> {
            Thread vt = Thread.ofVirtual().start(() ->
                log.info("Virtual thread active: {}", Thread.currentThread().isVirtual())
            );
            vt.join();
        };
    }
}
