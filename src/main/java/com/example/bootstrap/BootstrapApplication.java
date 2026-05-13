package com.example.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot application entry point for the spring-bootstrap reactive monolith template.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BootstrapApplication {

    /**
     * Launches the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BootstrapApplication.class, args);
    }
}
