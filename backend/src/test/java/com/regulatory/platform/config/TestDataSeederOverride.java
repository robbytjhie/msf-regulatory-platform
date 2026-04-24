package com.regulatory.platform.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Overrides the production DataSeeder during tests.
 * Each test class seeds its own data via IntegrationTestBase.seedUsers()
 * so the prod seed data doesn't interfere with assertions.
 */
@TestConfiguration
public class TestDataSeederOverride {

    @Bean
    @Primary
    CommandLineRunner seedData() {
        // No-op: tests manage their own data
        return args -> {};
    }
}
