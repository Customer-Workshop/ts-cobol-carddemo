package com.carddemo.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real PostgreSQL 16 via Testcontainers (target state §5, wave 1). Flyway runs V1 plus the
 * R__seed_test_data fixture; the datasource comes from the container through @ServiceConnection.
 *
 * <p>The container is a JVM-wide singleton started once here and never stopped by JUnit: the Spring
 * test context (and its connection pool) is cached across subclasses, so a per-class
 * {@code @Container} lifecycle would leave the second subclass talking to a stopped container.
 * Ryuk removes the container when the JVM exits.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"
})
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
