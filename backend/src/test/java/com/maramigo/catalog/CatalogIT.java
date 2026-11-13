package com.maramigo.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.AfterAll;
import java.sql.DriverManager;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises Flyway, Hibernate and real HTTP against PostgreSQL. @author Maram Abbas */
@ActiveProfiles("demo")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogIT {
    private static PostgreSQLContainer<?> container;
    private static final String schema = "it_" + UUID.randomUUID().toString().replace("-", "");
    private static String url, user, password;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) throws Exception {
        url = System.getenv("TEST_DB_URL");
        if (url == null || url.isBlank()) {
            container = new PostgreSQLContainer<>("postgres:17.11");
            container.start();
            url = container.getJdbcUrl(); user = container.getUsername(); password = container.getPassword();
        } else {
            user = System.getenv("TEST_DB_USER"); password = System.getenv("TEST_DB_PASSWORD");
        }
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        properties.add("maramigo.mail.worker.enabled",()->"false");
        properties.add("spring.datasource.hikari.connection-init-sql",()->"SET search_path TO "+schema);
        properties.add("spring.datasource.hikari.maximum-pool-size",()->"4");
        properties.add("spring.datasource.hikari.minimum-idle",()->"1");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.default-schema", () -> schema);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
    }
    @AfterAll static void cleanup() throws Exception {
        try {
            if (url != null) try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.createStatement()) { statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
        } finally { if (container != null) container.stop(); }
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test void databaseRejectsNegativeStockAndDuplicateSku() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".products " +
                "(sku,name,description,category,price,stock_quantity) VALUES ('NEG','Negative','Test','Test',1,-1)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".products " +
                "(sku,name,description,category,price,stock_quantity) VALUES ('MG-001','Duplicate','Test','Test',1,1)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void inactiveProductsAreHiddenEvenWhenRequestedById() {
        Long id = jdbc.queryForObject("INSERT INTO " + schema + ".products " +
                "(sku,name,description,category,price,stock_quantity,active) VALUES " +
                "('HIDDEN','Hidden piece','Test','Test',10,1,false) RETURNING id", Long.class);
        try {
            var response = http.getForEntity("/api/products/" + id, String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(404);
            var list = http.getForObject("/api/products?search=Hidden", CatalogController.CatalogPage.class);
            assertThat(list).isNotNull();
            assertThat(list.items()).isEmpty();
        } finally { jdbc.update("DELETE FROM " + schema + ".products WHERE id=?", id); }
    }

    @Test void paginationReturnsStableNonOverlappingPages() {
        var first = http.getForObject("/api/products?size=2&page=0", CatalogController.CatalogPage.class);
        var second = http.getForObject("/api/products?size=2&page=1", CatalogController.CatalogPage.class);
        assertThat(first).isNotNull(); assertThat(second).isNotNull();
        assertThat(first.items()).hasSize(2); assertThat(second.items()).hasSize(2);
        assertThat(first.items().get(1).id()).isLessThan(second.items().get(0).id());
        assertThat(first.total()).isEqualTo(4); assertThat(second.total()).isEqualTo(4);
    }

    @Test void categoryFiltersAndPriceOrderingUseStoredValues() {
        var category = http.getForObject("/api/products?category=Accessories", CatalogController.CatalogPage.class);
        assertThat(category).isNotNull();
        assertThat(category.items()).singleElement().satisfies(p -> assertThat(p.sku()).isEqualTo("MG-003"));
        var asc = http.getForObject("/api/products?sort=price-asc", CatalogController.CatalogPage.class);
        var desc = http.getForObject("/api/products?sort=price-desc", CatalogController.CatalogPage.class);
        assertThat(asc).isNotNull(); assertThat(desc).isNotNull();
        assertThat(asc.items().get(0).price()).isEqualByComparingTo("39.00");
        assertThat(desc.items().get(0).price()).isEqualByComparingTo("249.00");
        var combined = http.getForObject("/api/products?search=Silk&category=Wear", CatalogController.CatalogPage.class);
        assertThat(combined).isNotNull(); assertThat(combined.items()).isEmpty();
    }

    @Test void basketLookupUsesFreshValuesAndOmitsInactiveAndUnknownProducts() {
        Long id = jdbc.queryForObject("INSERT INTO " + schema + ".products " +
            "(sku,name,description,category,price,stock_quantity,active) VALUES " +
            "('LOOKUP-TEST','Lookup test','Isolated fixture','Living',10,2,true) RETURNING id", Long.class);
        try {
            var first = http.getForObject("/api/products/lookup?ids="+id+","+id+",999999", CatalogController.ProductView[].class);
            assertThat(first).hasSize(1);
            assertThat(first[0].price()).isEqualByComparingTo("10.00");
            jdbc.update("UPDATE "+schema+".products SET price=9.25,stock_quantity=0 WHERE id=?",id);
            var fresh = http.getForObject("/api/products/lookup?ids="+id, CatalogController.ProductView[].class);
            assertThat(fresh).hasSize(1);
            assertThat(fresh[0].price()).isEqualByComparingTo("9.25");
            assertThat(fresh[0].stockQuantity()).isZero();
            jdbc.update("UPDATE "+schema+".products SET active=false WHERE id=?",id);
            assertThat(http.getForObject("/api/products/lookup?ids="+id, CatalogController.ProductView[].class)).isEmpty();
        } finally { jdbc.update("DELETE FROM "+schema+".products WHERE id=?",id); }
    }

    @Test void basketLookupRejectsInvalidOrUnboundedRequests() {
        String manyIds = java.util.stream.LongStream.rangeClosed(1,51).mapToObj(Long::toString).collect(java.util.stream.Collectors.joining(","));
        for (String ids : java.util.List.of("0","-1","", "invalid",manyIds)) {
            assertThat(http.getForEntity("/api/products/lookup?ids="+ids,String.class).getStatusCode().value()).isEqualTo(400);
        }
    }
    @Test void demonstrationStoresHaveSeparateCatalogs(){
        var stores=http.getForObject("/api/companies",CompanyController.View[].class);
        assertThat(stores).hasSize(3);
        var noir=http.getForObject("/api/products?companyId=2",CatalogController.CatalogPage.class);
        assertThat(noir).isNotNull();assertThat(noir.items()).singleElement().satisfies(p->assertThat(p.sku()).isEqualTo("MG-002"));
        var luna=http.getForObject("/api/products?companyId=3",CatalogController.CatalogPage.class);
        assertThat(luna).isNotNull();assertThat(luna.items()).hasSize(2).allSatisfy(p->assertThat(p.companyId()).isEqualTo(3L));
    }

    @Test void seededCatalogAndCaseInsensitiveSearchComeFromPostgres() {
        var all = http.getForEntity("/api/products", CatalogController.CatalogPage.class);
        assertThat(all.getStatusCode().value()).isEqualTo(200);
        assertThat(all.getBody()).isNotNull();
        assertThat(all.getBody().total()).isEqualTo(4);
        var filtered = http.getForObject("/api/products?search=SILK", CatalogController.CatalogPage.class);
        assertThat(filtered).isNotNull();
        assertThat(filtered.items()).singleElement().satisfies(p -> {
            assertThat(p.sku()).isEqualTo("MG-001");
            assertThat(p.price()).isEqualByComparingTo("129.00");
        });
        var missing = http.getForEntity("/api/products/99999", String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
    }
}
