package com.fdl.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fdl.platform.support.AbstractSqliteDataJpaTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationsTest extends AbstractSqliteDataJpaTest {

    private static final String NOW = "2026-01-01T10:00:00Z";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAppliedAllMigrationsFromScratch() {
        var versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);

        assertThat(versions).containsExactly("1", "2", "3");
    }

    @Test
    void allTablesAndIndexesExist() {
        assertThat(names("table")).contains("users", "orders", "order_items", "order_status_history");
        assertThat(names("index")).contains(
                "idx_orders_status_created_at", "idx_order_items_order_id", "idx_order_status_history_order_id");
    }

    @Test
    void emailMustBeUniqueIgnoringCase() {
        insertUser("u1", "maria@example.com");

        assertConstraintViolation(() -> insertUser("u2", "MARIA@Example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRONTO", "recebido", ""})
    void orderStatusOutsideTheFiveValuesIsRejected(String status) {
        insertUser("u1", "maria@example.com");

        assertConstraintViolation(() -> insertOrder("o1", "u1", status));
    }

    @Test
    void negativeOrderTotalIsRejected() {
        insertUser("u1", "maria@example.com");

        insertOrder("o1", "u1", "RECEBIDO");
        assertConstraintViolation(() -> jdbc.update("UPDATE orders SET total_cents = -1 WHERE id = 'o1'"));
    }

    @Test
    void orderRequiresAnExistingCreator() {
        assertConstraintViolation(() -> insertOrder("o1", "nobody", "RECEBIDO"));
    }

    @Test
    void itemQuantityMustBePositiveAndPriceNonNegative() {
        insertUser("u1", "maria@example.com");
        insertOrder("o1", "u1", "RECEBIDO");

        assertConstraintViolation(() -> insertItem("i1", "o1", 0, 100));
        assertConstraintViolation(() -> insertItem("i2", "o1", 1, -1));
        assertThat(insertItem("i3", "o1", 2, 0)).isEqualTo(1);
    }

    @Test
    void deletingAnOrderCascadesToItemsAndHistory() {
        insertUser("u1", "maria@example.com");
        insertOrder("o1", "u1", "RECEBIDO");
        insertItem("i1", "o1", 1, 5490);
        jdbc.update("INSERT INTO order_status_history (id, order_id, from_status, to_status, changed_by, changed_at) "
                + "VALUES ('h1', 'o1', NULL, 'RECEBIDO', 'u1', ?)", NOW);

        jdbc.update("DELETE FROM orders WHERE id = 'o1'");

        assertThat(count("order_items")).isZero();
        assertThat(count("order_status_history")).isZero();
    }

    @Test
    void historyRejectsInvalidStatusesButAcceptsNullOrigin() {
        insertUser("u1", "maria@example.com");
        insertOrder("o1", "u1", "RECEBIDO");
        var sql = "INSERT INTO order_status_history (id, order_id, from_status, to_status, changed_by, changed_at) "
                + "VALUES (?, 'o1', ?, ?, 'u1', ?)";

        assertThat(jdbc.update(sql, "h1", null, "RECEBIDO", NOW)).isEqualTo(1);
        assertConstraintViolation(() -> jdbc.update(sql, "h2", "XYZ", "RECEBIDO", NOW));
        assertConstraintViolation(() -> jdbc.update(sql, "h3", "RECEBIDO", "XYZ", NOW));
    }

    // O tradutor de exceções do Spring não conhece os códigos do SQLite: a violação chega como
    // DataAccessException com SQLITE_CONSTRAINT_* na mensagem.
    private static void assertConstraintViolation(ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("SQLITE_CONSTRAINT");
    }

    private List<String> names(String type) {
        return jdbc.queryForList("SELECT name FROM sqlite_master WHERE type = ?", String.class, type);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void insertUser(String id, String email) {
        jdbc.update("INSERT INTO users (id, name, email, password_hash, created_at, updated_at) "
                + "VALUES (?, 'Maria', ?, 'hash', ?, ?)", id, email, NOW, NOW);
    }

    private void insertOrder(String id, String userId, String status) {
        jdbc.update("INSERT INTO orders (id, customer_name, customer_phone, street, number, neighborhood, city, "
                + "state, zip_code, status, total_cents, created_by, created_at, updated_at) "
                + "VALUES (?, 'Maria Silva', '11987654321', 'Rua A', '10', 'Centro', 'São Paulo', 'SP', '01001000', "
                + "?, 0, ?, ?, ?)", id, status, userId, NOW, NOW);
    }

    private int insertItem(String id, String orderId, int quantity, int unitPriceCents) {
        return jdbc.update("INSERT INTO order_items (id, order_id, name, quantity, unit_price_cents) "
                + "VALUES (?, ?, 'Pizza', ?, ?)", id, orderId, quantity, unitPriceCents);
    }
}
