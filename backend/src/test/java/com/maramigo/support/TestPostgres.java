package com.maramigo.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import java.util.UUID;

/** Uses real PostgreSQL in Docker or an explicitly configured local test server. @author Maram Abbas */
public final class TestPostgres implements AutoCloseable {
    public final String schema = "it_" + UUID.randomUUID().toString().replace("-", "");
    private PostgreSQLContainer<?> container;
    private String url, user, password;
    public void configure(DynamicPropertyRegistry properties) throws Exception {
        url = System.getenv("TEST_DB_URL");
        if (url == null || url.isBlank()) {
            container = new PostgreSQLContainer<>("postgres:17.11"); container.start();
            url = container.getJdbcUrl(); user = container.getUsername(); password = container.getPassword();
        } else { user = System.getenv("TEST_DB_USER"); password = System.getenv("TEST_DB_PASSWORD"); }
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
        }
        properties.add("maramigo.mail.worker.enabled",()->"false");
        properties.add("spring.datasource.hikari.maximum-pool-size",()->"4");
        properties.add("spring.datasource.hikari.minimum-idle",()->"1");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.default-schema", () -> schema);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + schema);
    }
    @Override public void close() throws Exception {
        try {
            if (url != null) try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        } finally { if (container != null) container.stop(); }
    }
}
